package com.toasty.domain.order.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.order.controller.dto.request.OrderCreateRequest;
import com.toasty.domain.order.controller.dto.response.CustomerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrdersResponse;
import com.toasty.domain.order.controller.dto.response.OrderCreateResponse;
import com.toasty.domain.order.controller.dto.response.OrderPaymentResponse;
import com.toasty.domain.order.entity.CustomerOrderPageCommand;
import com.toasty.domain.order.entity.OrderStatusFilter;
import com.toasty.domain.order.service.OrderService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer Order", description = "구매자 주문내역 API")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;

    @Operation(
            summary = "주문하기",
            description =
                    "상품 번호와 수량만 보내면 서버가 상품 가격과 스토어 배송비로 결제금액을 계산해 주문을 만듭니다."
                            + " 배송지는 내 기본 배송지를 주문 시점 값으로 복사합니다. 이 시점에 재고를 선점하므로,"
                            + " 남은 재고보다 많이 주문하면 결제창을 열기 전에 409로 거절됩니다. 응답의 sessionId로"
                            + " 결제창을 열고, 결제창이 POINT3_CAPTURE_READY를 보내면 결제 승인 API를 부르세요."
                            + " 라이브 시청 화면에서 구매하면 liveId를 함께 보내세요. 셀러 라이브탭의 방송별 판매 집계에 쓰입니다.")
    @CustomerOnly
    @PostMapping
    public ApiResponse<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request, @LoginUser AuthUser customer) {
        return ApiResponse.ok(
                orderService.createOrder(
                        request.toCommand(customer.userId(), customer.customerId())));
    }

    @Operation(
            summary = "결제 승인",
            description =
                    "주문에 붙은 결제 세션을 승인해 결제를 끝냅니다. 결제창에서 POINT3_CAPTURE_READY를 받은 뒤에"
                            + " 부르세요. 승인이 끝나면 주문이 배송대기로 넘어갑니다. 승인이 거절되면 409이고 주문은"
                            + " 결제실패로 남습니다. 결과를 확인하지 못하면 503이며, 이때는 실패가 아니므로 잠시 후"
                            + " 다시 부르거나 주문내역에서 확인하세요. 이미 결제가 끝난 주문은 그대로 성공합니다.")
    @CustomerOnly
    @PostMapping("/{orderId}/payment")
    public ApiResponse<OrderPaymentResponse> payOrder(
            @PathVariable Long orderId, @LoginUser AuthUser customer) {
        return ApiResponse.ok(orderService.payOrder(orderId, customer.customerId()));
    }

    @Operation(
            summary = "구매자 주문내역 조회",
            description =
                    "구매자 주문탭을 채웁니다. 화면을 열 때 파라미터 없이 부르고, 목록 끝에 닿을 때마다 직전 응답의"
                            + " nextCursor를 그대로 넘겨 이어 받으세요. hasNext가 false면 더 부르지 않습니다."
                            + " 상태 칩은 status로 거릅니다. 칩에 붙는 건수(counts)는 스크롤 중에 바뀌지 않아"
                            + " 첫 요청에서만 내려주고 이어 받을 때는 null입니다. 스토어 화면으로 가려면 응답의"
                            + " sellerId를 쓰세요.")
    @CustomerOnly
    @GetMapping
    public ApiResponse<CustomerOrdersResponse> findMyOrders(
            @Parameter(description = "상태 칩. 생략하면 전체") @RequestParam(defaultValue = "ALL")
                    OrderStatusFilter status,
            @Parameter(description = "직전 응답의 nextCursor. 첫 요청에는 넣지 않는다")
                    @RequestParam(required = false)
                    Long cursor,
            @LoginUser AuthUser customer) {
        return ApiResponse.ok(
                orderService.findCustomerOrders(
                        new CustomerOrderPageCommand(customer.customerId(), status, cursor)));
    }

    @Operation(
            summary = "구매자 주문 상세 조회",
            description =
                    "주문 상세 화면을 채웁니다. 상품과 배송지는 주문 시점 값이라 셀러가 상품을 지우거나 배송지를"
                            + " 바꿔도 그대로 남고, 스토어 이름은 지금 값을 보여줍니다. 본인 주문이 아니면 404입니다.")
    @CustomerOnly
    @GetMapping("/{orderId}")
    public ApiResponse<CustomerOrderDetailResponse> getMyOrder(
            @PathVariable Long orderId, @LoginUser AuthUser customer) {
        return ApiResponse.ok(orderService.findCustomerOrder(orderId, customer.customerId()));
    }
}
