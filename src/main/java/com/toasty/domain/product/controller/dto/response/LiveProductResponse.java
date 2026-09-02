package com.toasty.domain.product.controller.dto.response;

import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.LiveProductStatus;
import com.toasty.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

/** 라이브에 편성된 상품 하나를 보여줄 때 쓴다. 가격·재고는 상품에서, 노출 순서와 판매 상태는 편성에서 가져온다. */
public record LiveProductResponse(
        @Schema(description = "상품 번호") Long productId,
        @Schema(description = "편성 번호. 방송 중 고정·구매에서 쓴다") Long liveProductId,
        @Schema(description = "상품명") String name,
        @Schema(description = "가격(원)") int price,
        @Schema(description = "재고") int stockQuantity,
        @Schema(description = "대표 이미지 주소") String imageUrl,
        @Schema(description = "라이브 내 노출 순서. 0번 상품의 사진이 라이브 카드 썸네일이 된다") int displayOrder,
        @Schema(description = "편성 상태") LiveProductStatus status) {

    public static LiveProductResponse of(
            Product product, LiveProduct liveProduct, String imageUrl) {
        return new LiveProductResponse(
                product.getId(),
                liveProduct.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                imageUrl,
                liveProduct.getDisplayOrder(),
                liveProduct.getStatus());
    }
}
