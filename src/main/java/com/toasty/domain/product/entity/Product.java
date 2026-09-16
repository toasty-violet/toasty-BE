package com.toasty.domain.product.entity;

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

/** 셀러가 라이브에서 판매하려고 등록한 상품이다. 품절은 따로 표시하지 않고 재고가 0이면 품절로 본다. */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_type", nullable = false, length = 20)
    private SalesType salesType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    private Product(Long sellerId, String name, int price, int stockQuantity, String description) {
        this.sellerId = sellerId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.salesType = SalesType.LIVE;
        this.description = description;
    }

    /** 셀러가 라이브 수정 화면에서 상품 내용을 고친다. */
    public void update(String name, int price, int stockQuantity, String description) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = description;
    }

    /** 방송 중에는 가격과 재고만 고칠 수 있다. 상품명·사진은 라이브 시작 전에 정해진다. */
    public void changePriceAndStock(int price, int stockQuantity) {
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public boolean isSoldOut() {
        return stockQuantity <= 0;
    }

    public boolean hasEnoughStock(int quantity) {
        return stockQuantity >= quantity;
    }

    /** 주문이 재고를 선점한다. 일반판매 상품이 다 팔리면 판매를 닫는다. */
    // 재고가 남아 있는지는 부르는 쪽이 행을 잠근 뒤에 확인한다.
    public void decreaseStock(int quantity) {
        this.stockQuantity -= quantity;
        if (salesType == SalesType.GENERAL && isSoldOut()) {
            this.salesType = SalesType.SOLD_OUT;
        }
    }

    /** 결제가 실패하거나 취소돼 선점했던 재고를 되돌린다. 다 팔려 닫았던 판매도 함께 되돌린다. */
    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
        if (salesType == SalesType.SOLD_OUT && !isSoldOut()) {
            this.salesType = SalesType.GENERAL;
        }
    }

    /** 라이브가 끝나면 재고가 남은 상품은 일반판매로 넘기고, 다 팔린 상품은 판매를 닫는다. */
    // 이미 넘어간 상품은 다시 라이브로 되돌리지 않는다.
    public void closeLiveSales() {
        if (salesType != SalesType.LIVE) {
            return;
        }
        this.salesType = isSoldOut() ? SalesType.SOLD_OUT : SalesType.GENERAL;
    }

    public static Product createForLive(Long sellerId, ProductCreateCommand command) {
        return new Product(
                sellerId,
                command.name(),
                command.price(),
                command.stockQuantity(),
                command.description());
    }
}
