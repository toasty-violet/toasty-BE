package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

/** 홈 베스트 아이템의 카드 한 장을 채운다. */
// 사진이나 스토어를 못 찾아도 화면이 카드 자리를 잡도록 null이어도 키를 남긴다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BestProductResponse(
        @Schema(description = "상품 번호. 상품 상세 진입에 쓴다") Long productId,
        @Schema(description = "스토어 이름") String shopName,
        String name,
        @Schema(description = "원 단위") int price,
        @Schema(description = "대표 사진 주소") String imageUrl) {

    public static BestProductResponse of(Product product, String shopName, String imageUrl) {
        return new BestProductResponse(
                product.getId(), shopName, product.getName(), product.getPrice(), imageUrl);
    }
}
