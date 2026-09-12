package com.toasty.domain.order.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.order.controller.dto.response.CustomerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrdersResponse;
import com.toasty.domain.order.entity.CustomerOrderPageCommand;
import com.toasty.domain.order.entity.OrderStatusFilter;
import com.toasty.domain.order.service.OrderService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
