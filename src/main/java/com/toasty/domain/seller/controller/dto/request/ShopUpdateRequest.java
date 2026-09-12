package com.toasty.domain.seller.controller.dto.request;

import com.toasty.domain.seller.entity.ShopUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ShopUpdateRequest(
        @Schema(description = "스토어 이름", example = "토스티상회")
                @NotBlank(message = "스토어 이름은 필수입니다.") @Size(max = 20, message = "스토어 이름은 20자를 넘을 수 없습니다.") String shopName,
        @Schema(
                        description =
                                "샵 이미지가 저장된 위치. 사진을 바꾸지 않으면 조회로 받은 값을 그대로 보내고, 비우면 기본 이미지로 돌아간다",
                        example = "sellers/images/1/2026/09/11/9f1c....jpg")
                @Size(max = 500, message = "샵 이미지 위치는 500자를 넘을 수 없습니다.") String shopImageObjectKey,
        @Schema(description = "스토어 소개", example = "예쁜 빈티지 옷을 모아두는 중")
                @Size(max = 500, message = "스토어 소개는 500자를 넘을 수 없습니다.") String description,
        @Schema(description = "기본 배송비(원)", example = "3000")
                @NotNull(message = "기본 배송비는 필수입니다.") @PositiveOrZero(message = "기본 배송비는 0원 이상이어야 합니다.") Integer baseShippingFee,
        @Schema(description = "무료배송 기준 금액(원). 0이면 무료배송 기준을 두지 않는다", example = "50000")
                @NotNull(message = "무료배송 기준 금액은 필수입니다.") @PositiveOrZero(message = "무료배송 기준 금액은 0원 이상이어야 합니다.") Integer freeShippingThreshold,
        @Schema(description = "도서 산간 배송비(원). 기본 배송비에 더해 받는다", example = "3000")
                @NotNull(message = "도서 산간 배송비는 필수입니다.") @PositiveOrZero(message = "도서 산간 배송비는 0원 이상이어야 합니다.") Integer remoteAreaShippingFee) {

    public ShopUpdateCommand toCommand(Long userId, Long sellerId) {
        return new ShopUpdateCommand(
                userId,
                sellerId,
                shopName,
                shopImageObjectKey,
                description,
                baseShippingFee,
                freeShippingThreshold,
                remoteAreaShippingFee);
    }
}
