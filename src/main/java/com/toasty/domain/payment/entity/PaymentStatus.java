package com.toasty.domain.payment.entity;

/** 우리 원장에 남기는 결제 진행 상태. point3 세션 상태를 결제 한 건 기준으로 좁힌 값이다. */
public enum PaymentStatus {
    // 세션을 만들었고 아직 승인하지 않았다
    PENDING,
    CAPTURED,
    // 승인이 거절됐거나 승인 기한을 넘겼다
    FAILED,
    // 재시도와 조회로도 결과를 가리지 못했다. 실패로 확정하지 않는다
    UNKNOWN,
    // 승인 뒤에 전액 취소했다
    REFUNDED
}
