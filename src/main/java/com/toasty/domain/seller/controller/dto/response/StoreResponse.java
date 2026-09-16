package com.toasty.domain.seller.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 구매자가 보는 스토어 화면의 머리말 전체. */
// 대표 이미지나 소개가 비어도 화면이 자리를 잡아야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StoreResponse(
        @Schema(description = "셀러 번호", example = "1") Long sellerId,
        @Schema(description = "스토어 대표 이미지 주소") String shopImageUrl,
        @Schema(description = "스토어 이름", example = "토스티상회") String shopName,
        @Schema(description = "팔로워 수", example = "0") long followerCount,
        @Schema(description = "등록한 상품 수. 품절과 라이브에서 팔 상품도 포함한다", example = "0") long productCount,
        @Schema(description = "스토어 소개") String description,
        @Schema(description = "요청한 유저가 이 스토어를 팔로우 중인지. 비로그인이면 항상 false") boolean following,
        @Schema(description = "이 스토어의 배송비") StoreShippingFeeResponse shippingFee) {

    public static StoreResponse of(
            StoreDetailResponse store, long followerCount, long productCount, boolean following) {
        return new StoreResponse(
                store.sellerId(),
                store.shopImageUrl(),
                store.shopName(),
                followerCount,
                productCount,
                store.description(),
                following,
                store.shippingFee());
    }
}
