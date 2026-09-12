package com.toasty.domain.order.entity;

/** 셀러가 주문 한 건에 운송장을 등록한다. */
public record OrderShipmentCommand(
        Long orderId, Long sellerId, Courier courier, String trackingNumber) {}
