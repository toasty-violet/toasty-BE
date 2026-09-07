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

    public static Product createForLive(Long sellerId, ProductCreateCommand command) {
        return new Product(
                sellerId,
                command.name(),
                command.price(),
                command.stockQuantity(),
                command.description());
    }
}
