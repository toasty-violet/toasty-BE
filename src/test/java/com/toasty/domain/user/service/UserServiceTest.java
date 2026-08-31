package com.toasty.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("이미 가입된 카카오 유저는 기존 유저를 반환하고 isOnboardingCompleted는 온보딩 완료 여부를 따른다")
    void loginWithKakao_existingUser() {
        String kakaoId = "12345";
        User existingUser = User.createFromKakao(kakaoId);
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.of(existingUser));

        UserLoginResult result = userService.loginWithKakao(kakaoId);

        assertThat(result.user()).isEqualTo(existingUser);
        assertThat(result.isOnboardingCompleted()).isEqualTo(existingUser.isOnboardingCompleted());
    }

    @Test
    @DisplayName("처음 로그인하는 카카오 유저는 신규 저장하고 isOnboardingCompleted는 false다")
    void loginWithKakao_newUser() {
        String kakaoId = "99999";
        User savedUser = User.createFromKakao(kakaoId);
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        UserLoginResult result = userService.loginWithKakao(kakaoId);

        assertThat(result.isOnboardingCompleted()).isFalse();
        assertThat(result.user().getKakaoId()).isEqualTo(kakaoId);
        verify(userRepository).save(any(User.class));
    }
}
