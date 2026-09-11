package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 구매자가 보는 상품 상세 화면을 채운다. */
// 상세 설명은 비어 있을 수 있는데, 그때도 화면이 그 자리를 접어야 한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductDetailResponse(
        Long productId,
        @Schema(description = "이 상품을 파는 셀러. 스토어 정보는 이 번호로 따로 부른다") Long sellerId,
        String name,
        @Schema(description = "원 단위") int price,
        String description,
        @Schema(description = "노출 순서대로. 첫 장이 대표 사진") List<String> imageUrls,
        @Schema(description = "같은 스토어의 다른 상품. 이 상품은 빠져 있다")
                List<StoreProductResponse> otherProducts) {

    public static ProductDetailResponse of(
            Product product, List<String> imageUrls, List<StoreProductResponse> otherProducts) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                imageUrls,
                otherProducts);
    }
}
