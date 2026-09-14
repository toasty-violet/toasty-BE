package com.toasty.domain.order.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 셀러 주문탭의 카드 한 장을 채운다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerOrderResponse(
        @Schema(description = "주문 번호. 주문 상세 진입에 쓴다") Long orderId,
        @Schema(description = "SHIPPING_PENDING이면 배송대기, SHIPPED면 발송완료") OrderStatus status,
        @Schema(description = "결제가 끝난 시각") LocalDateTime paidAt,
        @Schema(description = "받는사람. 카드에 구매자 자리로 뜬다") String receiverName,
        String productName,
        @Schema(description = "주문 시점의 대표 사진 주소") String productImageUrl,
        int quantity,
        @Schema(description = "총 결제금액. 원 단위") int totalAmount,
        @Schema(description = "택배사 이름. 발송완료가 아니면 null", example = "CJ 대한통운") String courierName,
        @Schema(description = "운송장 번호. 발송완료가 아니면 null", example = "394817503811")
                String trackingNumber) {

    public static SellerOrderResponse from(Order order) {
        return new SellerOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getPaidAt(),
                order.getReceiverName(),
                order.getProductName(),
                order.getProductImageUrl(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.courierName(),
                order.getTrackingNumber());
    }
}
