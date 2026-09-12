package com.toasty.domain.follow.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** 홈 화면의 인기 스토어 카드 한 장을 채운다. */
// 대표 이미지가 비어도 화면이 자리를 잡아야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TopStoreResponse(
        @Schema(description = "셀러 번호. 스토어 상세 진입에 쓴다") Long sellerId,
        @Schema(description = "스토어 이름") String shopName,
        @Schema(description = "스토어 대표 이미지 주소") String shopImageUrl,
        @Schema(description = "팔로워 수") long followerCount,
        @Schema(description = "스토어가 판매중인 상품 수") int productCount,
        @Schema(description = "요청한 유저가 이 스토어를 팔로우 중인지. 비로그인이면 항상 false") boolean following) {

    public static TopStoreResponse of(
            SellerProfileResponse shop, long followerCount, int productCount, boolean following) {
        return new TopStoreResponse(
                shop.sellerId(),
                shop.shopName(),
                shop.shopImageUrl(),
                followerCount,
                productCount,
                following);
    }
}
