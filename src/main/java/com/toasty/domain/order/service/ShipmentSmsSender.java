package com.toasty.domain.order.service;

import com.toasty.domain.order.client.SolapiSmsClient;
import com.toasty.domain.order.entity.OrderShippedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 운송장이 등록되면 받는사람에게 배송 시작 문자를 보낸다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentSmsSender {

    private static final String TEXT_FORMAT =
            """
            [ toasty 상품 출고 안내 ]
            주문하신 상품이 발송되었습니다.

            배송 조회까지 평일 기준 1~2일 정도 소요될 수 있습니다.
            상품 수령까지 조금만 기다려주세요!

            ■주문 정보
            주문번호: %s
            상품명: %s

            ■배송 정보
            택배사: %s
            송장번호: %s\
            """;

    private final SolapiSmsClient smsClient;

    // 문자는 운송장 등록이 확정된 뒤에만 보내고, 실패해도 등록을 되돌리지 않는다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(OrderShippedEvent event) {
        try {
            smsClient.send(event.receiverPhone(), textOf(event));
        } catch (Exception e) {
            log.warn("배송 시작 문자 발송 실패 - orderId={}", event.orderId(), e);
        }
    }

    static String textOf(OrderShippedEvent event) {
        return TEXT_FORMAT.formatted(
                event.orderNumber(),
                event.productName(),
                event.courierName(),
                event.trackingNumber());
    }
}
