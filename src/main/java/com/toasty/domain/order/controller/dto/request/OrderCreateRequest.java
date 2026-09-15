package com.toasty.domain.order.controller.dto.request;

import com.toasty.domain.order.entity.OrderCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 주문하기 요청. 금액은 받지 않는다. 상품 가격과 배송비는 서버가 읽어 계산한다. */
public record OrderCreateRequest(
        @Schema(description = "살 상품 번호") @NotNull(message = "상품 번호는 필수입니다.") Long productId,
        @Schema(description = "수량", example = "1")
                @Min(value = 1, message = "수량은 1개 이상이어야 합니다.") @Max(value = 99, message = "수량은 99개를 넘을 수 없습니다.") int quantity) {

    public OrderCreateCommand toCommand(Long userId, Long customerId) {
        return new OrderCreateCommand(userId, customerId, productId, quantity);
    }
}
