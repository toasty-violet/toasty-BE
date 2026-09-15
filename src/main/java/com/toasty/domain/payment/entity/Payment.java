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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * point3에 만든 결제 세션 한 건이다. 승인·조회·취소가 모두 sessionId로 이뤄진다.
 *
 * <p>payerId 확보용 세션은 승인을 호출하지 않아 주문·금액·상태가 비어 있다.
 */
@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    // 실패 사유는 화면에 그대로 뿌리지 않고 원장에만 남기므로 컬럼 길이에서 자른다.
    private static final int FAILURE_MESSAGE_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentStatus status;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    // point3가 준 승인 결과 코드
    @Column(name = "outcome_code", length = 50)
    private String outcomeCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    private Payment(
            Long userId,
            Long orderId,
            String sessionId,
            Integer amount,
            PaymentPurpose purpose,
            PaymentStatus status) {
        this.userId = userId;
        this.orderId = orderId;
        this.sessionId = sessionId;
        this.amount = amount;
        this.purpose = purpose;
        this.status = status;
    }

    public static Payment createForPayerId(Long userId, String sessionId) {
        return new Payment(
                userId, null, sessionId, null, PaymentPurpose.PAYER_ID_REGISTRATION, null);
    }

    /** 상품 결제용 세션. 승인 전이라 아직 결제금액이 빠져나가지 않았다. */
    public static Payment createForPurchase(PurchaseSessionCommand command, String sessionId) {
        return new Payment(
                command.userId(),
                command.orderId(),
                sessionId,
                command.amount(),
                PaymentPurpose.PRODUCT_PURCHASE,
                PaymentStatus.PENDING);
    }

    /** 이 유저가 payerId를 받으려고 만든 세션인지 판단한다. */
    public boolean isPayerIdSessionOf(Long userId) {
        return this.userId.equals(userId) && purpose == PaymentPurpose.PAYER_ID_REGISTRATION;
    }

    public boolean isCaptured() {
        return status == PaymentStatus.CAPTURED;
    }

    /** 승인이 끝나 결제금액이 빠져나갔음을 남긴다. */
    public void capture(String outcomeCode, LocalDateTime capturedAt) {
        this.status = PaymentStatus.CAPTURED;
        this.outcomeCode = outcomeCode;
        this.capturedAt = capturedAt;
        this.failureMessage = null;
    }

    /** 승인이 거절됐거나 기한을 넘겨 결제되지 않았음을 남긴다. */
    public void fail(String outcomeCode, String failureMessage) {
        this.status = PaymentStatus.FAILED;
        this.outcomeCode = outcomeCode;
        this.failureMessage = shorten(failureMessage);
    }

    /** 결과를 가리지 못했음을 남긴다. 실패가 아니므로 나중에 조회로 다시 확정해야 한다. */
    public void markUnknown(String failureMessage) {
        this.status = PaymentStatus.UNKNOWN;
        this.failureMessage = shorten(failureMessage);
    }

    /** 승인된 결제를 전액 취소했음을 남긴다. */
    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= FAILURE_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, FAILURE_MESSAGE_MAX_LENGTH);
    }
}
