package com.toasty.domain.order.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 구매자 주문내역의 카드 한 장을 채운다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CustomerOrderResponse(
        @Schema(description = "주문 번호. 주문 상세 진입에 쓴다") Long orderId,
        @Schema(description = "SHIPPING_PENDING이면 배송대기, SHIPPED면 발송완료") OrderStatus status,
        @Schema(description = "결제가 끝난 시각") LocalDateTime paidAt,
        @Schema(description = "산 스토어. 스토어 화면 진입에 쓴다") Long sellerId,
        @Schema(description = "스토어 이름") String shopName,
        String productName,
        @Schema(description = "주문 시점의 대표 사진 주소") String productImageUrl,
        int quantity,
        @Schema(description = "총 결제금액. 원 단위") int totalAmount,
        @Schema(description = "발송완료가 아니면 null") String courier,
        @Schema(description = "발송완료가 아니면 null") String trackingNumber) {

    public static CustomerOrderResponse of(Order order, String shopName) {
        return new CustomerOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getPaidAt(),
                order.getSellerId(),
                shopName,
                order.getProductName(),
                order.getProductImageUrl(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getCourier(),
                order.getTrackingNumber());
    }
}
