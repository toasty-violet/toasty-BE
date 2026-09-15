package com.toasty.domain.payment.client.dto;

/**
 * point3 결제 취소 요청 본문. 면세금액과 부가세는 point3가 계산해 주지 않아 취소분의 세금 구성을 직접 채워 보낸다.
 *
 * <p>refundTaxFreeAmount와 refundVat의 합은 refundAmount를 넘을 수 없다.
 */
public record PaymentRefundCreateRequest(
        int refundAmount, int refundTaxFreeAmount, int refundVat, String reason) {}
