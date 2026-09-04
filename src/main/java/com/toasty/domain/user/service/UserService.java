package com.toasty.domain.user.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.service.CustomerService;
import com.toasty.domain.user.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.exception.UserErrorCode;
import com.toasty.domain.user.repository.AuthUserProjection;
import com.toasty.domain.user.repository.UserRepository;
import com.toasty.global.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerService customerService;

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

    /* 카카오 식별자로 유저를 조회하고, 없다면 신규 가입한다. */
    @Transactional
    public User loginWithKakao(String kakaoId) {
        return userRepository
                .findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.createFromKakao(kakaoId)));
    }
}
