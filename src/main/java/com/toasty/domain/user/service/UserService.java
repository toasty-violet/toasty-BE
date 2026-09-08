package com.toasty.domain.user.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.service.CustomerService;
import com.toasty.domain.live.service.LiveService;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.domain.user.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.entity.UserWithdrawCommand;
import com.toasty.domain.user.exception.UserErrorCode;
import com.toasty.domain.user.repository.AuthUserProjection;
import com.toasty.domain.user.repository.UserRepository;
import com.toasty.global.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final SellerService sellerService;
    private final LiveService liveService;
    private final TransactionTemplate transactionTemplate;

    /** 인증 필터가 액세스 토큰의 userId로 호출한다. 토큰은 유효해도 그 사이 탈퇴했을 수 있어, 판단은 호출한 쪽에 맡기고 Optional로 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<AuthUser> findAuthUser(Long userId) {
        return userRepository.findAuthUserById(userId).map(UserService::toAuthUser);
    }

    private static AuthUser toAuthUser(AuthUserProjection projection) {
        String role = projection.getRole();
        return new AuthUser(
                projection.getUserId(),
                role == null ? null : Role.valueOf(role),
                projection.getCustomerId(),
                projection.getSellerId());
    }

    /** 로그인한 유저 본인의 정보를 조회한다. */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return userRepository
                .findById(userId)
                .map(UserMeResponse::from)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    /** 입력한 닉네임을 이미 다른 유저가 쓰고 있는지 확인한다. 자기 닉네임을 그대로 둔 경우는 중복으로 보지 않는다. */
    @Transactional(readOnly = true)
    public NicknameSearchResponse searchNickname(String nickname, Long userId) {
        boolean duplicated =
                userId == null
                        ? userRepository.existsByNickname(nickname)
                        : userRepository.existsByNicknameAndIdNot(nickname, userId);
        return new NicknameSearchResponse(duplicated);
    }

    /** 구매자 온보딩 제출을 받아 역할을 구매자로 설정하고 닉네임을 확정한다. */
    @Transactional
    public void completeCustomerOnboarding(CustomerOnboardingCommand command) {
        User user =
                userRepository
                        .findById(command.userId())
                        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.isOnboardingCompleted()) {
            throw new CustomException(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED);
        }
        if (userRepository.existsByNicknameAndIdNot(command.nickname(), user.getId())) {
            throw new CustomException(UserErrorCode.USER_NICKNAME_DUPLICATED);
        }
        user.completeOnboarding(Role.CUSTOMER, command.nickname());
        flushNicknameOrThrow();
        customerService.createForOnboarding(command);
    }

    /** 판매자 온보딩 제출을 받아 역할을 판매자로 설정하고 스토어 이름을 닉네임으로 확정한다. */
    @Transactional
    public void completeSellerOnboarding(SellerOnboardingCommand command) {
        User user =
                userRepository
                        .findById(command.userId())
                        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.isOnboardingCompleted()) {
            throw new CustomException(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED);
        }
        if (userRepository.existsByNicknameAndIdNot(command.shopName(), user.getId())) {
            throw new CustomException(UserErrorCode.USER_NICKNAME_DUPLICATED);
        }
        user.completeOnboarding(Role.SELLER, command.shopName());
        flushNicknameOrThrow();
        sellerService.createForOnboarding(command);
    }

    /**
     * 닉네임 변경만 DB에 먼저 반영해, 다른 유저가 같은 닉네임을 선점했으면 닉네임 중복(409)으로 돌려준다.
     *
     * <p>다른 테이블에 쓰기 전에 호출해야 한다. 그래야 여기서 나는 제약 위반이 uk_users_nickname 하나로 좁혀진다.
     */
    private void flushNicknameOrThrow() {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(UserErrorCode.USER_NICKNAME_DUPLICATED, e);
        }
    }

    /* 카카오 식별자로 유저를 조회하고, 없다면 신규 가입한다. 탈퇴한 유저는 조회되지 않아 새 유저로 가입된다. */
    @Transactional
    public User loginWithKakao(String kakaoId) {
        return userRepository
                .findByKakaoIdAndDeletedAtIsNull(kakaoId)
                .orElseGet(() -> userRepository.save(User.createFromKakao(kakaoId)));
    }

    /**
     * 유저를 탈퇴 처리하고, 카카오 연결을 끊는 데 쓰도록 지워지기 전의 카카오 회원번호를 돌려준다.
     *
     * <p>유저 행은 남고, 판매자·구매자 정보도 거래 상대방 식별을 위해 남는다. 배송지는 지우고, 판매자라면 아직 끝나지 않은 라이브를 먼저 정리한다.
     */
    // 라이브 정리는 IVS와 S3를 부르느라 수 초가 걸려, DB 커넥션을 잡지 않도록 트랜잭션 밖에 둔다.
    // 라이브만 정리되고 탈퇴가 실패하면 유저는 그대로 남으므로 다시 요청하면 된다.
    public String withdraw(UserWithdrawCommand command) {
        if (command.sellerId() != null) {
            liveService.cleanUpForSellerWithdrawal(command.sellerId());
        }

        return transactionTemplate.execute(
                status -> {
                    User user =
                            userRepository
                                    .findById(command.userId())
                                    .filter(found -> !found.isWithdrawn())
                                    .orElseThrow(
                                            () ->
                                                    new CustomException(
                                                            UserErrorCode.USER_NOT_FOUND));
                    if (command.customerId() != null) {
                        customerService.deleteAddresses(command.customerId());
                    }
                    // 탈퇴 처리가 회원번호를 지우므로 먼저 빼둔다.
                    String kakaoId = user.getKakaoId();
                    user.withdraw();
                    return kakaoId;
                });
    }
}
