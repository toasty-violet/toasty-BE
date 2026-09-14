package com.toasty.domain.order.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 구매자 주문 상세 화면을 채운다. */
// 운송장과 상세 주소는 비어 있을 수 있는데, 그때도 화면이 그 자리를 그려야 한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CustomerOrderDetailResponse(
        Long orderId,
        @Schema(description = "화면에 보여주는 주문번호", example = "20260915-100234") String orderNumber,
        @Schema(description = "SHIPPING_PENDING이면 배송대기, SHIPPED면 발송완료") OrderStatus status,
        @Schema(description = "산 스토어. 스토어 화면 진입에 쓴다") Long sellerId,
        @Schema(description = "스토어 이름") String shopName,
        String productName,
        @Schema(description = "주문 시점의 대표 사진 주소") String productImageUrl,
        int quantity,
        @Schema(description = "받는사람") String receiverName,
        String receiverPhone,
        String postalCode,
        String address,
        String detailAddress,
        @Schema(description = "결제가 끝난 시각") LocalDateTime paidAt,
        @Schema(description = "상품 금액. 원 단위") int productPrice,
        @Schema(description = "배송비. 원 단위") int shippingFee,
        @Schema(description = "총 결제금액. 원 단위") int totalAmount,
        @Schema(description = "발송완료가 아니면 null") String courier,
        @Schema(description = "발송완료가 아니면 null") String trackingNumber) {

    public static CustomerOrderDetailResponse of(Order order, String shopName) {
        return new CustomerOrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getSellerId(),
                shopName,
                order.getProductName(),
                order.getProductImageUrl(),
                order.getQuantity(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getPostalCode(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getPaidAt(),
                order.getProductPrice(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getCourier(),
                order.getTrackingNumber());
    }
}
