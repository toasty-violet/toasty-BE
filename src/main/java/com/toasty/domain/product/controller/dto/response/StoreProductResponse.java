package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

/** 스토어 화면의 상품 카드 한 장을 채운다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StoreProductResponse(
        @Schema(description = "상품 번호. 상품 상세 진입에 쓴다") Long productId,
        String name,
        @Schema(description = "원 단위") int price,
        @Schema(description = "대표 사진 주소") String imageUrl) {

    public static StoreProductResponse of(Product product, String imageUrl) {
        return new StoreProductResponse(
                product.getId(), product.getName(), product.getPrice(), imageUrl);
    }
}
