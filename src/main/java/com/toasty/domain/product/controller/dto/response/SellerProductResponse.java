package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 셀러 상품탭의 카드 한 장을 채운다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerProductResponse(
        @Schema(description = "상품 번호. 수정·삭제에 그대로 쓴다") Long productId,
        String name,
        @Schema(description = "원 단위") int price,
        @Schema(description = "재고 수량") int stockQuantity,
        @Schema(description = "LIVE면 라이브 예정, GENERAL이면 판매중") SalesType salesType,
        @Schema(description = "대표 사진 주소") String imageUrl) {

    public static SellerProductResponse of(Product product, String imageUrl) {
        return new SellerProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getSalesType(),
                imageUrl);
    }
}
