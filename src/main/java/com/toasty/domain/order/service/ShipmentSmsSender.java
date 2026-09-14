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
        return "[toasty] 주문하신 %s 상품이 발송되었어요.\n%s %s"
                .formatted(event.productName(), event.courierName(), event.trackingNumber());
    }
}
