package com.toasty.domain.seller.entity;

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
 * 판매자 Entity.
 *
 * <p>닉네임은 여기 두지 않고 users Entity의 nickname을 쓴다.
 */
@Entity
@Getter
@Table(name = "sellers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 샵 이미지
    @Column(name = "shop_image_url", length = 500)
    private String shopImageUrl;

    // 스토어 소개
    @Column(length = 500)
    private String description;

    // 대표자 이름
    @Column(name = "seller_name", length = 50)
    private String sellerName;

    // 대표자 연락처
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 사업자등록번호. 하이픈 없이 10자리로 저장한다
    @Column(name = "business_number", length = 10, unique = true)
    private String businessNumber;

    // 정산 계좌 은행
    @Enumerated(EnumType.STRING)
    @Column(name = "bank", length = 20)
    private Bank bank;

    // 정산 계좌번호
    @Column(name = "account_number", length = 30)
    private String accountNumber;

    private Seller(Long userId) {
        this.userId = userId;
    }

    public static Seller createForOnboarding(Long userId) {
        return new Seller(userId);
    }
}
