package com.toasty.domain.product.entity;

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

/** 상품 사진이다. 순서가 0인 사진을 대표 이미지로 쓴다. */
@Entity
@Getter
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseTimeEntity {

    private static final int MAIN_IMAGE_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private ProductImage(Long productId, String imageUrl, int displayOrder) {
        this.productId = productId;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    public static ProductImage createMain(Long productId, String imageUrl) {
        return new ProductImage(productId, imageUrl, MAIN_IMAGE_ORDER);
    }
}
