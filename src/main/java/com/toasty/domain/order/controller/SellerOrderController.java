package com.toasty.domain.order.controller;

import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.annotation.SellerOnly;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.order.controller.dto.request.OrderShipmentRequest;
import com.toasty.domain.order.controller.dto.response.SellerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrdersResponse;
import com.toasty.domain.order.entity.OrderStatusFilter;
import com.toasty.domain.order.entity.SellerOrderPageCommand;
import com.toasty.domain.order.service.OrderService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller Order", description = "셀러 주문 관리 API")
@RestController
@RequestMapping("/api/v1/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;

    @Operation(
            summary = "셀러 주문 목록 조회",
            description =
                    "셀러 주문탭을 채웁니다. 화면을 열 때 파라미터 없이 부르고, 목록 끝에 닿을 때마다 직전 응답의"
                            + " nextCursor를 그대로 넘겨 이어 받으세요. hasNext가 false면 더 부르지 않습니다."
                            + " 상태 칩은 status로 거릅니다. 칩에 붙는 건수(counts)는 스크롤 중에 바뀌지 않아"
                            + " 첫 요청에서만 내려주고 이어 받을 때는 null입니다.")
    @SellerOnly
    @GetMapping
    public ApiResponse<SellerOrdersResponse> findMyOrders(
            @Parameter(description = "상태 칩. 생략하면 전체") @RequestParam(defaultValue = "ALL")
                    OrderStatusFilter status,
            @Parameter(description = "직전 응답의 nextCursor. 첫 요청에는 넣지 않는다")
                    @RequestParam(required = false)
                    Long cursor,
            @LoginUser AuthUser seller) {
        return ApiResponse.ok(
                orderService.findSellerOrders(
                        new SellerOrderPageCommand(seller.sellerId(), status, cursor)));
    }

    @Operation(
            summary = "셀러 주문 상세 조회",
            description =
                    "주문 상세 화면을 채웁니다. 상품과 배송지는 주문 시점 값이라, 셀러가 상품을 지우거나 구매자가"
                            + " 배송지를 바꿔도 그대로 남습니다. 본인 주문이 아니면 404입니다.")
    @SellerOnly
    @GetMapping("/{orderId}")
    public ApiResponse<SellerOrderDetailResponse> getMyOrder(
            @PathVariable Long orderId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(orderService.findSellerOrder(orderId, seller.sellerId()));
    }

    @Operation(
            summary = "운송장 등록",
            description =
                    "택배사와 운송장 번호를 등록해 주문을 발송완료로 넘깁니다. 주문탭 목록의 배송대기 카드와 주문 상세,"
                            + " 두 화면이 같이 부릅니다. 상태 칩 건수도 함께 바뀌므로 성공하면 목록을 다시 받으세요."
                            + " 한 번 등록하면 되돌리거나 고칠 수 없어, 이미 발송완료면 409입니다."
                            + " 본인 주문이 아니면 404입니다.")
    @SellerOnly
    @PatchMapping("/{orderId}/shipment")
    public ApiResponse<Void> registerShipment(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderShipmentRequest request,
            @LoginUser AuthUser seller) {
        orderService.registerShipment(request.toCommand(orderId, seller.sellerId()));
        return ApiResponse.ok();
    }
}
