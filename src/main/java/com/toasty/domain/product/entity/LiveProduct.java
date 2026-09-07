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

/** 특정 상품이 특정 라이브에서 어떻게 노출·판매되는지를 담는다. 가격과 재고는 여기 두지 않는다 — {@code Product}가 단일 진실 원천이다. */
@Entity
@Getter
@Table(name = "live_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_id", nullable = false)
    private Long liveId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveProductStatus status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private LiveProduct(Long liveId, Long productId, int displayOrder) {
        this.liveId = liveId;
        this.productId = productId;
        this.status = LiveProductStatus.SCHEDULED;
        this.displayOrder = displayOrder;
    }

    /** 셀러가 상품 순서를 바꾸면 그 순서를 반영한다. */
    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /** 상품을 라이브에 편성한다. 방송 전이라 목록에만 보이고 아직 구매할 수 없다. */
    public static LiveProduct schedule(Long liveId, Long productId, int displayOrder) {
        return new LiveProduct(liveId, productId, displayOrder);
    }
}
