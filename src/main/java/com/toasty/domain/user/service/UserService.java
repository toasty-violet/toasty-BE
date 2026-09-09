package com.toasty.domain.user.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.service.CustomerService;
import com.toasty.domain.follow.service.FollowService;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.domain.user.controller.dto.response.UserRoleResponse;
import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.entity.UserWithdrawCommand;
import com.toasty.domain.user.exception.UserErrorCode;
import com.toasty.domain.user.repository.AuthUserProjection;
import com.toasty.domain.user.repository.UserRepository;
import com.toasty.global.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final SellerService sellerService;
    private final FollowService followService;
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

    /** 로그인한 유저의 역할을 조회한다. */
    @Transactional(readOnly = true)
    public UserRoleResponse getRole(Long userId) {
        return userRepository
                .findById(userId)
                .map(UserRoleResponse::from)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    /** 구매자 온보딩 제출을 받아 역할을 구매자로 설정하고 구매자 정보를 만든다. */
    @Transactional
    public void completeCustomerOnboarding(CustomerOnboardingCommand command) {
        startOnboarding(command.userId(), Role.CUSTOMER);
        customerService.createForOnboarding(command);
    }

    /** 판매자 온보딩 제출을 받아 역할을 판매자로 설정하고 판매자 정보를 만든다. */
    @Transactional
    public void completeSellerOnboarding(SellerOnboardingCommand command) {
        startOnboarding(command.userId(), Role.SELLER);
        sellerService.createForOnboarding(command);
    }

    private void startOnboarding(Long userId, Role role) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.isOnboardingCompleted()) {
            throw new CustomException(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED);
        }
        user.completeOnboarding(role);
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
     * <p>유저 행은 남고, 판매자·구매자 정보도 거래 상대방 식별을 위해 남는다. 표시명과 배송지, 팔로우 관계는 놓아준다.
     *
     * <p>판매자로 탈퇴해도 유저 하나가 지워지는 것이라, 그가 팔로우한 관계와 그를 팔로우한 관계를 함께 정리한다.
     */
    public String withdraw(UserWithdrawCommand command) {
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
                        customerService.withdraw(command.customerId());
                        followService.deleteByCustomerId(command.customerId());
                    }
                    if (command.sellerId() != null) {
                        sellerService.withdraw(command.sellerId());
                        followService.deleteBySellerId(command.sellerId());
                    }
                    // 탈퇴 처리가 회원번호를 지우므로 먼저 빼둔다.
                    String kakaoId = user.getKakaoId();
                    user.withdraw();
                    return kakaoId;
                });
    }
}
