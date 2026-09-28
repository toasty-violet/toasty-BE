package com.toasty.domain.payment.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * point3에 보낸 취소 요청 한 건이다. 부분 취소가 있어 주문 하나에 여러 건이 쌓인다.
 *
 * <p>요청을 보내기 전에 먼저 저장한다. 응답을 받지 못해도 어떤 취소를 걸었는지 남아 있어야 결과를 다시 확인할 수 있다.
 */
@Entity
@Getter
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    // point3가 돌려준 취소 건 식별자. 응답을 받지 못한 요청은 비어 있다
    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "tax_free_amount", nullable = false)
    private int taxFreeAmount;

    @Column(nullable = false)
    private int vat;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    private Refund(
            Long orderId,
            String sessionId,
            int amount,
            int taxFreeAmount,
            int vat,
            String reason,
            String idempotencyKey) {
        this.orderId = orderId;
        this.sessionId = sessionId;
        this.amount = amount;
        this.taxFreeAmount = taxFreeAmount;
        this.vat = vat;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.status = RefundStatus.PROCESSING;
    }

    /** point3에 보낼 취소 요청을 남긴다. 응답을 받기 전이라 처리 중으로 시작한다. */
    public static Refund request(
            PaymentRefundCommand command, int taxFreeAmount, int vat, String idempotencyKey) {
        return new Refund(
                command.orderId(),
                command.sessionId(),
                command.amount(),
                taxFreeAmount,
                vat,
                command.reason(),
                idempotencyKey);
    }

    /** point3가 취소를 끝냈음을 남긴다. */
    public void complete(String refundId) {
        this.refundId = refundId;
        this.status = RefundStatus.COMPLETED;
    }

    /** 아직 끝나지 않은 취소임을 남긴다. 재개 호출로 이어서 처리해야 한다. */
    public void keepProcessing(String refundId) {
        this.refundId = refundId;
        this.status = RefundStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == RefundStatus.COMPLETED;
    }
}
