package com.toasty.domain.seller.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 판매자 본인이 보는 스토어 정보 전체. */
// 대표 이미지나 소개가 비어도 화면이 자리를 잡아야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ShopResponse(
        @Schema(description = "스토어 번호. 스토어 주소 toast.kr/shop/{sellerId}를 만들 때 쓴다", example = "1")
                Long sellerId,
        @Schema(description = "스토어 대표 이미지 주소") String shopImageUrl,
        @Schema(description = "샵 이미지가 저장된 위치. 스토어 정보 수정에 그대로 실어 보낸다") String shopImageObjectKey,
        @Schema(description = "스토어 이름", example = "토스티상회") String shopName,
        @Schema(description = "팔로워 수", example = "0") long followerCount,
        @Schema(description = "등록한 상품 수. 품절도 포함한다", example = "0") long productCount,
        @Schema(description = "스토어 소개") String description,
        @Schema(description = "판매 내역") ShopSalesSummaryResponse salesSummary,
        @Schema(description = "배송비 정책") ShopShippingFeeResponse shippingFee) {

    public static ShopResponse of(ShopDetailResponse shop, long followerCount, long productCount) {
        return new ShopResponse(
                shop.sellerId(),
                shop.shopImageUrl(),
                shop.shopImageObjectKey(),
                shop.shopName(),
                followerCount,
                productCount,
                shop.description(),
                shop.salesSummary(),
                shop.shippingFee());
    }
}
