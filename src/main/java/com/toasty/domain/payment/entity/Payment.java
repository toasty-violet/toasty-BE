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

/** point3에 만든 결제 세션 한 건이다. 승인·조회·취소가 모두 sessionId로 이뤄진다. */
@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose;

    private Payment(Long userId, String sessionId, PaymentPurpose purpose) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.purpose = purpose;
    }

    public static Payment createForPayerId(Long userId, String sessionId) {
        return new Payment(userId, sessionId, PaymentPurpose.PAYER_ID_REGISTRATION);
    }
}
