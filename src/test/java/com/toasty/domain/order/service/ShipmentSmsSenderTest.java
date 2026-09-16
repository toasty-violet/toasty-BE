package com.toasty.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.toasty.domain.order.client.SolapiSmsClient;
import com.toasty.domain.order.entity.OrderShippedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

@DisplayName("배송 시작 문자")
class ShipmentSmsSenderTest {

    private static final OrderShippedEvent EVENT =
            new OrderShippedEvent(
                    31L,
                    "20260916-8F3A21C0",
                    "010-2345-6789",
                    "아이보리 골지 가디건",
                    "CJ 대한통운",
                    "394817503811");

    private SolapiSmsClient smsClient;
    private ShipmentSmsSender sender;

    @BeforeEach
    void setUp() {
        smsClient = mock(SolapiSmsClient.class);
        sender = new ShipmentSmsSender(smsClient);
    }

    @Test
    @DisplayName("받는사람 번호로 주문 정보와 배송 정보를 담아 보낸다")
    void 받는사람에게_보낸다() {
        sender.send(EVENT);

        verify(smsClient)
                .send(
                        "010-2345-6789",
                        """
                        [ toasty 상품 출고 안내 ]
                        주문하신 상품이 발송되었습니다.

                        배송 조회까지 평일 기준 1~2일 정도 소요될 수 있습니다.
                        상품 수령까지 조금만 기다려주세요!

                        ■주문 정보
                        주문번호: 20260916-8F3A21C0
                        상품명: 아이보리 골지 가디건

                        ■배송 정보
                        택배사: CJ 대한통운
                        송장번호: 394817503811\
                        """);
    }

    @Test
    @DisplayName("문자 발송이 실패해도 예외를 밖으로 내보내지 않는다")
    void 실패해도_던지지_않는다() {
        willThrow(new ResourceAccessException("timeout"))
                .given(smsClient)
                .send(anyString(), anyString());

        assertThatCode(() -> sender.send(EVENT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주문번호와 송장번호가 문구에 들어간다")
    void 주문번호와_송장번호를_담는다() {
        assertThat(ShipmentSmsSender.textOf(EVENT))
                .contains("주문번호: 20260916-8F3A21C0")
                .contains("송장번호: 394817503811");
    }
}
