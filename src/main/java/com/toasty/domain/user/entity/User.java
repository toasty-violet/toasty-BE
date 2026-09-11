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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저 Entity. 계정 식별과 역할만 가진다.
 *
 * <p>온보딩에서 채우는 상세 정보와 화면에 뜨는 표시명은 역할에 따라 Customer·Seller가 나눠 가진다.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    private static final String WITHDRAWN_KAKAO_ID_PREFIX = "withdrawn_";

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

    // 탈퇴 시각 — null이면 이용 중인 유저
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String kakaoId, Role role) {
        this.kakaoId = kakaoId;
        this.role = role;
    }

    /** 카카오 최초 로그인 시점에는 kakaoId 외의 정보가 없다. 나머지는 온보딩에서 채운다. */
    public static User createFromKakao(String kakaoId) {
        return new User(kakaoId, null);
    }

    /** 온보딩은 역할 선택과 상세 정보 입력을 한 번에 제출받으므로, 역할이 정해졌다면 상세 정보도 채워져 있다. */
    public boolean isOnboardingCompleted() {
        return role != null;
    }

    /** 온보딩 제출로 역할을 확정한다. */
    public void completeOnboarding(Role role) {
        this.role = role;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /**
     * 유저를 탈퇴 처리한다. 행은 남기고 탈퇴 시각만 기록한다.
     *
     * <p>구매자·판매자 정보는 거래 상대방을 식별하는 데 필요해 함께 지우지 않는다.
     */
    // kakaoId는 unique라 값을 그대로 두면 같은 계정으로 재가입할 수 없다.
    // 그래서 다른 유저와 겹치지 않는 값으로 바꿔 자리를 비운다.
    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        this.kakaoId = WITHDRAWN_KAKAO_ID_PREFIX + id;
    }
}
