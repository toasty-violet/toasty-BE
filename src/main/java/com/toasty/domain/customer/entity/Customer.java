package com.toasty.domain.customer.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매자 Entity.
 *
 * <p>닉네임은 여기 두지 않고 users Entity의 nickname을 쓴다.
 */
@Entity
@Getter
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 이름
    @Column(length = 50)
    private String name;

    // 휴대폰 번호
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 결제 연동 시 P3로부터 받는 결제자 식별자
    @Column(name = "payer_id", length = 50)
    private String payerId;

    private Customer(Long userId) {
        this.userId = userId;
    }

    public static Customer createForOnboarding(Long userId) {
        return new Customer(userId);
    }
}
