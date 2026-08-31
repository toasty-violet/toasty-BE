package com.toasty.domain.user.service;

import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
