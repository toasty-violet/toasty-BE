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
            new OrderShippedEvent(31L, "010-2345-6789", "아이보리 골지 가디건", "CJ 대한통운", "394817503811");

    private SolapiSmsClient smsClient;
    private ShipmentSmsSender sender;

    @BeforeEach
    void setUp() {
        smsClient = mock(SolapiSmsClient.class);
        sender = new ShipmentSmsSender(smsClient);
    }

    @Test
    @DisplayName("받는사람 번호로 상품명과 운송장을 담아 보낸다")
    void 받는사람에게_보낸다() {
        sender.send(EVENT);

        verify(smsClient)
                .send(
                        "010-2345-6789",
                        "[toasty] 주문하신 아이보리 골지 가디건 상품이 발송되었어요.\nCJ 대한통운 394817503811");
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
    @DisplayName("문구는 두 줄이다")
    void 두_줄이다() {
        assertThat(ShipmentSmsSender.textOf(EVENT).lines()).hasSize(2);
    }
}
