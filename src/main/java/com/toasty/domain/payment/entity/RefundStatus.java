package com.toasty.domain.payment.entity;

/** 취소 한 건의 처리 상태. point3가 주는 completed·processing을 그대로 따른다. */
public enum RefundStatus {
    PROCESSING,
    COMPLETED
}
