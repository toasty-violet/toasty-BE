package com.toasty.domain.user.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** 인증 필터가 액세스 토큰의 userId로 호출한다. 토큰은 유효해도 그 사이 탈퇴했을 수 있어, 판단은 호출한 쪽에 맡기고 Optional로 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<AuthUser> findAuthUser(Long userId) {
        return userRepository
                .findById(userId)
                .map(user -> new AuthUser(user.getId(), user.getRole()));
    }

    /* 카카오 식별자로 유저를 조회하고, 없다면 신규 가입한다. */
    @Transactional
    public UserLoginResult loginWithKakao(String kakaoId) {
        return userRepository
                .findByKakaoId(kakaoId)
                .map(user -> new UserLoginResult(user, user.isOnboardingCompleted()))
                .orElseGet(
                        () ->
                                new UserLoginResult(
                                        userRepository.save(User.createFromKakao(kakaoId)), false));
    }
}
