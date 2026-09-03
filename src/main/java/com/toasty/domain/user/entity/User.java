package com.toasty.domain.user.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* 유저 Entity. */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    private static final String TEMPORARY_NICKNAME_PREFIX = "user_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카카오 로그인 식별자
    @Column(name = "kakao_id", nullable = false, unique = true, length = 50)
    private String kakaoId;

    // 유저 역할 (판매자, 구매자) — 온보딩 전까지 null
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Role role;

    // 휴대폰 번호 — 온보딩 전까지 null
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 이름 — 온보딩 전까지 null
    @Column(length = 50)
    private String name;

    // 닉네임 — 가입 시점에는 임시 닉네임이 들어가고 온보딩에서 교체된다
    @Column(length = 20, nullable = false, unique = true)
    private String nickname;

    // 결제 연동 시 PG사로부터 받는 결제자 식별자 — 연동 전까지 null
    @Column(name = "payer_id", length = 50)
    private String payerId;

    private User(String kakaoId, Role role, String phoneNumber, String name, String nickname) {
        this.kakaoId = kakaoId;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.name = name;
        this.nickname = nickname;
    }

    public static User create(String kakaoId, Role role, String phoneNumber, String name) {
        return new User(kakaoId, role, phoneNumber, name, generateTemporaryNickname());
    }

    /** 카카오 최초 로그인 시점에는 kakaoId 외의 정보가 없다. 나머지는 온보딩에서 채운다. */
    public static User createFromKakao(String kakaoId) {
        return new User(kakaoId, null, null, null, generateTemporaryNickname());
    }

    // 닉네임은 not null이라 온보딩 전까지 쓸 값을 가입 시점에 만들어 넣는다
    private static String generateTemporaryNickname() {
        return TEMPORARY_NICKNAME_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public boolean isOnboardingCompleted() {
        return role != null;
    }
}
