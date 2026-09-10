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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매자 Entity. 스토어로 화면에 뜨는 상점명을 가진다. */
@Entity
@Getter
@Table(name = "sellers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseTimeEntity {

    private static final String WITHDRAWN_SHOP_NAME_PREFIX = "del_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 스토어로 화면에 뜨는 이름
    @Column(name = "shop_name", length = 20, nullable = false, unique = true)
    private String shopName;

    // 샵 이미지가 버킷에 저장된 위치. 보여줄 주소는 읽는 쪽에서 조합한다
    @Column(name = "shop_image_object_key", length = 500)
    private String shopImageObjectKey;

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

    // 기본 배송비
    @Column(name = "base_shipping_fee", nullable = false)
    private int baseShippingFee;

    // 이 금액 이상 사면 배송비를 받지 않는다. 0이면 무료배송 기준이 없다
    @Column(name = "free_shipping_threshold", nullable = false)
    private int freeShippingThreshold;

    // 도서 산간 지역에 기본 배송비와 별도로 더 받는 금액
    @Column(name = "remote_area_shipping_fee", nullable = false)
    private int remoteAreaShippingFee;

    // 누적 판매 건수
    // TODO: 주문 도메인이 생기면 주문 확정 시점에 갱신한다
    @Column(name = "total_sales_count", nullable = false)
    private int totalSalesCount;

    // 이 스토어에서 한 번이라도 산 구매자 수
    // TODO: 주문 도메인이 생기면 주문 확정 시점에 갱신한다
    @Column(name = "total_buyer_count", nullable = false)
    private int totalBuyerCount;

    // 누적 판매액. 원 단위 합계라 int 상한을 넘길 수 있어 long으로 둔다
    // TODO: 주문 도메인이 생기면 주문 확정 시점에 갱신한다
    @Column(name = "total_sales_amount", nullable = false)
    private long totalSalesAmount;

    private Seller(
            Long userId,
            String shopName,
            String shopImageObjectKey,
            String description,
            String sellerName,
            String phoneNumber,
            String businessNumber,
            Bank bank,
            String accountNumber) {
        this.userId = userId;
        this.shopName = shopName;
        this.shopImageObjectKey = shopImageObjectKey;
        this.description = description;
        this.sellerName = sellerName;
        this.phoneNumber = phoneNumber;
        this.businessNumber = businessNumber;
        this.bank = bank;
        this.accountNumber = accountNumber;
    }

    /** 온보딩 제출 시점에 만들어진다. */
    public static Seller createForOnboarding(SellerOnboardingCommand command) {
        return new Seller(
                command.userId(),
                command.shopName(),
                command.shopImageObjectKey(),
                command.description(),
                command.sellerName(),
                command.phoneNumber(),
                command.businessNumber(),
                command.bank(),
                command.accountNumber());
    }

    /** 스토어 정보 수정으로 상점명과 대표 이미지, 소개와 배송비를 바꾼다. */
    // 판매 내역은 주문이 쌓여 만들어지는 값이라 여기서 건드리지 않는다.
    public void updateShop(
            String shopName,
            String shopImageObjectKey,
            String description,
            int baseShippingFee,
            int freeShippingThreshold,
            int remoteAreaShippingFee) {
        this.shopName = shopName;
        this.shopImageObjectKey = shopImageObjectKey;
        this.description = description;
        this.baseShippingFee = baseShippingFee;
        this.freeShippingThreshold = freeShippingThreshold;
        this.remoteAreaShippingFee = remoteAreaShippingFee;
    }

    /**
     * 탈퇴한 유저가 쓰던 상점명을 놓아준다.
     *
     * <p>판매자 행 자체는 거래 상대방을 식별하는 데 필요해 남긴다.
     */
    // 상점명은 unique라 값을 그대로 두면 그 이름을 아무도 다시 쓸 수 없다.
    // 상점명은 20자까지라 id 대신 길이가 고정된 값을 쓴다.
    public void withdraw() {
        this.shopName =
                WITHDRAWN_SHOP_NAME_PREFIX
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
