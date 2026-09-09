package com.toasty.domain.live.controller.dto.request;

import com.toasty.domain.product.entity.LiveProductUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 방송 중에는 가격과 재고만 고칠 수 있다. */
public record LiveProductUpdateRequest(
        @Schema(description = "가격(원)", example = "32000")
                @NotNull(message = "가격은 필수입니다.") @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.") Integer price,
        @Schema(description = "재고 수량", example = "1")
                @NotNull(message = "재고 수량은 필수입니다.") @Positive(message = "재고 수량은 1개 이상이어야 합니다.") Integer stockQuantity) {

    public LiveProductUpdateCommand toCommand(Long liveId, Long productId, Long sellerId) {
        return new LiveProductUpdateCommand(liveId, productId, sellerId, price, stockQuantity);
    }
}
