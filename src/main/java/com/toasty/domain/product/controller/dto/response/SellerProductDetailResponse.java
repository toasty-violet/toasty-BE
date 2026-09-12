package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 셀러 상품 수정 화면을 채운다. */
// 상세 설명은 비어 있을 수 있는데, 그때도 화면이 빈 칸을 그려야 한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerProductDetailResponse(
        Long productId,
        String name,
        @Schema(description = "원 단위") int price,
        @Schema(description = "재고 수량") int stockQuantity,
        String description,
        @Schema(description = "LIVE면 라이브 예정, GENERAL이면 판매중") SalesType salesType,
        @Schema(description = "노출 순서대로. 첫 장이 대표 사진") List<Image> images) {

    /** 수정 요청에 그대로 돌려보낼 objectKey와, 화면에 그릴 주소. */
    public record Image(String objectKey, String imageUrl) {}

    public static SellerProductDetailResponse of(Product product, List<Image> images) {
        return new SellerProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getDescription(),
                product.getSalesType(),
                images);
    }
}
