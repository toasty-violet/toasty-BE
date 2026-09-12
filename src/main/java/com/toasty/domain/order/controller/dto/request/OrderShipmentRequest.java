package com.toasty.domain.order.controller.dto.request;

import com.toasty.domain.order.entity.Courier;
import com.toasty.domain.order.entity.OrderShipmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 배송대기 주문에 운송장을 단다. */
public record OrderShipmentRequest(
        @Schema(description = "택배사 목록 조회로 받은 code", example = "CJ_LOGISTICS")
                @NotNull(message = "택배사는 필수입니다.") Courier courier,
        @Schema(description = "운송장 번호", example = "394817503811")
                @NotBlank(message = "운송장 번호는 필수입니다.") @Pattern(regexp = "^\\d{10,20}$", message = "운송장 번호는 하이픈 없이 10~20자리 숫자여야 합니다.") String trackingNumber) {

    public OrderShipmentCommand toCommand(Long orderId, Long sellerId) {
        return new OrderShipmentCommand(orderId, sellerId, courier, trackingNumber);
    }
}
