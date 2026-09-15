package com.toasty.domain.order.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 주문하기 응답. 이 세션으로 결제창을 열고, 결제창이 준비되면 결제 승인 API를 부른다. */
public record OrderCreateResponse(
        @Schema(description = "주문 번호") Long orderId,
        @Schema(description = "화면에 보여주는 주문번호", example = "20260916-8F3A21C0") String orderNumber,
        @Schema(description = "결제창을 열 때 쓰는 세션 아이디") String sessionId,
        @Schema(description = "상품 금액과 배송비를 더한 총 결제금액. 원 단위") int totalAmount,
        @Schema(
                        description =
                                "인증 URL의 payer_id로 넘기면 일부 인증 과정이 생략된다. 계좌 등록을 마치지 않은 구매자는 값이 없다",
                        example = "payer:01M23CH34X3XPRDKVE5V5P50JP")
                String payerId) {}
