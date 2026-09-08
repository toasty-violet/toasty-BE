package com.toasty.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import com.toasty.domain.seller.entity.SellerShop;
import com.toasty.domain.seller.service.SellerService;
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
    @Mock private SellerService sellerService;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("셀러 표시 정보는 스토어 이름을 nickname에서, 대표 이미지를 셀러에서 가져온다")
    void findSellerProfile_합쳐서_준다() {
        User seller = User.createFromKakao("12345");
        seller.completeOnboarding(com.toasty.domain.user.entity.Role.SELLER, "토스티샵");
        given(sellerService.findShop(7L))
                .willReturn(new SellerShop(7L, 3L, "https://cdn.example.com/shop.jpg"));
        given(userRepository.findById(3L)).willReturn(Optional.of(seller));

        SellerProfileResponse response = userService.findSellerProfile(7L);

        assertThat(response.sellerId()).isEqualTo(7L);
        assertThat(response.shopName()).isEqualTo("토스티샵");
        assertThat(response.shopImageUrl()).isEqualTo("https://cdn.example.com/shop.jpg");
    }

    @Test
    @DisplayName("이미 가입된 카카오 유저는 기존 유저를 반환한다")
    void loginWithKakao_existingUser() {
        String kakaoId = "12345";
        User existingUser = User.createFromKakao(kakaoId);
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.of(existingUser));

        User result = userService.loginWithKakao(kakaoId);

        assertThat(result).isEqualTo(existingUser);
    }

    @Test
    @DisplayName("처음 로그인하는 카카오 유저는 신규 저장한다")
    void loginWithKakao_newUser() {
        String kakaoId = "99999";
        User savedUser = User.createFromKakao(kakaoId);
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        User result = userService.loginWithKakao(kakaoId);

        assertThat(result.getKakaoId()).isEqualTo(kakaoId);
        verify(userRepository).save(any(User.class));
    }
}
