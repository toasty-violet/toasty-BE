package com.toasty.domain.customer.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구매자 Entity. 구매자로 화면에 뜨는 닉네임을 가진다. */
@Entity
@Getter
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    private static final String WITHDRAWN_NICKNAME_PREFIX = "del_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 구매자로 화면에 뜨는 이름
    @Column(length = 20, nullable = false, unique = true)
    private String nickname;

    // 이름
    @Column(length = 50)
    private String name;

    // 휴대폰 번호
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 결제 연동 시 P3로부터 받는 결제자 식별자
    @Column(name = "payer_id", length = 100)
    private String payerId;

    private Customer(
            Long userId, String nickname, String name, String phoneNumber, String payerId) {
        this.userId = userId;
        this.nickname = nickname;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.payerId = payerId;
    }

    /** 온보딩 제출 시점에 만들어진다. payerId는 계좌 등록 결제 세션에서 받아 함께 채운다. */
    public static Customer createForOnboarding(CustomerOnboardingCommand command, String payerId) {
        return new Customer(
                command.userId(),
                command.nickname(),
                command.name(),
                command.phoneNumber(),
                payerId);
    }

    /** 내 정보 수정으로 닉네임과 이름, 연락처를 바꾼다. */
    public void updateProfile(String nickname, String name, String phoneNumber) {
        this.nickname = nickname;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    /**
     * 탈퇴한 유저가 쓰던 닉네임을 놓아준다.
     *
     * <p>구매자 행 자체는 거래 상대방을 식별하는 데 필요해 남긴다.
     */
    // 닉네임은 unique라 값을 그대로 두면 그 닉네임을 아무도 다시 쓸 수 없다.
    // 닉네임은 20자까지라 id 대신 길이가 고정된 값을 쓴다.
    public void withdraw() {
        this.nickname =
                WITHDRAWN_NICKNAME_PREFIX
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
