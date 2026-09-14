package com.toasty.domain.follow.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.controller.dto.response.StoreProductResponse;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 홈 화면의 팔로우하는 스토어 한 칸을 채운다. */
// 스토어 이름이나 대표 이미지가 비어도 화면이 자리를 잡아야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FollowingStoreResponse(
        @Schema(description = "셀러 번호. 스토어 상세 진입에 쓴다") Long sellerId,
        @Schema(description = "스토어 이름") String shopName,
        @Schema(description = "스토어 대표 이미지 주소") String shopImageUrl,
        @Schema(description = "이 스토어가 판매중인 상품. 최신순으로 최대 3개") List<StoreProductResponse> products) {

    public static FollowingStoreResponse of(
            SellerProfileResponse shop, List<StoreProductResponse> products) {
        return new FollowingStoreResponse(
                shop.sellerId(), shop.shopName(), shop.shopImageUrl(), products);
    }
}
