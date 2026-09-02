package com.toasty.domain.user.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.exception.UserErrorCode;
import com.toasty.domain.user.repository.UserRepository;
import com.toasty.global.exception.CustomException;
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

    /** 로그인한 유저 본인의 정보를 조회한다. */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return userRepository
                .findById(userId)
                .map(UserMeResponse::from)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    /* 카카오 식별자로 유저를 조회하고, 없다면 신규 가입한다. */
    @Transactional
    public User loginWithKakao(String kakaoId) {
        return userRepository
                .findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.createFromKakao(kakaoId)));
    }
}
