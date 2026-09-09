package com.toasty.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.entity.SellerShop;
import com.toasty.domain.seller.exception.SellerErrorCode;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.global.config.SellerS3Properties;
import com.toasty.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SellerService")
class SellerServiceTest {

    private static final Long SELLER_ID = 7L;
    private static final Long USER_ID = 3L;

    private SellerRepository sellerRepository;
    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        sellerRepository = mock(SellerRepository.class);
        sellerService =
                new SellerService(
                        sellerRepository,
                        new SellerS3Properties(
                                "toasty-media", "https://cdn.example.com", "sellers/images/", 300));
    }

    @Nested
    @DisplayName("스토어 조회")
    class FindShop {

        @Test
        @DisplayName("대표 이미지 주소를 만들어 준다")
        void 사진_주소를_만든다() {
            given(sellerRepository.findById(SELLER_ID))
                    .willReturn(Optional.of(seller("sellers/images/3/2026/09/09/a.jpg")));

            SellerShop response = sellerService.findShop(SELLER_ID);

            assertThat(response.sellerId()).isEqualTo(SELLER_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.shopImageUrl())
                    .isEqualTo("https://cdn.example.com/sellers/images/3/2026/09/09/a.jpg");
        }

        @Test
        @DisplayName("대표 이미지가 없으면 주소도 null이다")
        void 사진이_없으면_null이다() {
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller(null)));

            assertThat(sellerService.findShop(SELLER_ID).shopImageUrl()).isNull();
        }

        @Test
        @DisplayName("없는 셀러면 SELLER_NOT_FOUND다")
        void 없으면_SELLER_NOT_FOUND다() {
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sellerService.findShop(SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(SellerErrorCode.SELLER_NOT_FOUND);
        }

        private Seller seller(String shopImageObjectKey) {
            Seller seller =
                    Seller.createForOnboarding(
                            new SellerOnboardingCommand(
                                    USER_ID,
                                    "토스티샵",
                                    "빈티지 옷 팝니다",
                                    shopImageObjectKey,
                                    "김대표",
                                    "01012345678",
                                    null,
                                    null,
                                    null));
            org.springframework.test.util.ReflectionTestUtils.setField(seller, "id", SELLER_ID);
            return seller;
        }
    }
}
