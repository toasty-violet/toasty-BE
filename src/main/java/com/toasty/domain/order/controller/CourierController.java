package com.toasty.domain.order.controller;

import com.toasty.domain.auth.annotation.SellerOnly;
import com.toasty.domain.order.controller.dto.response.CourierResponse;
import com.toasty.domain.order.service.OrderService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Courier", description = "택배사 API")
@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
public class CourierController {

    private final OrderService orderService;

    @Operation(
            summary = "택배사 목록 조회",
            description = "운송장 등록 칸의 택배사 드롭다운을 채웁니다. 목록은 바뀌지 않아 주문탭을 열 때 한 번만 부르면 됩니다.")
    @SellerOnly
    @GetMapping
    public ApiResponse<List<CourierResponse>> findCouriers() {
        return ApiResponse.ok(orderService.findCouriers());
    }
}
