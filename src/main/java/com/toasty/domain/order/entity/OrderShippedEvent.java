package com.toasty.domain.order.entity;

/** 운송장이 등록돼 발송완료로 넘어간 주문. 커밋 뒤 비동기로 읽혀 엔티티 대신 값을 담는다. */
public record OrderShippedEvent(
        Long orderId,
        String orderNumber,
        String receiverPhone,
        String productName,
        String courierName,
        String trackingNumber) {

    public static OrderShippedEvent from(Order order) {
        return new OrderShippedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getReceiverPhone(),
                order.getProductName(),
                order.courierName(),
                order.getTrackingNumber());
    }
}
