package com.toasty.domain.order.controller.dto.response;

import com.toasty.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 결제 승인 응답. 승인이 끝난 주문만 내려온다. */
public record OrderPaymentResponse(
        @Schema(description = "주문 번호") Long orderId,
        @Schema(description = "승인이 끝나면 SHIPPING_PENDING") OrderStatus status,
        @Schema(description = "결제가 끝난 시각") LocalDateTime paidAt) {}
