package com.toasty.domain.customer.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.controller.dto.response.CustomerProfileResponse;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer", description = "구매자 API")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final UserService userService;

    @Operation(
            summary = "구매자 내 정보 조회",
            description =
                    "구매자가 마이페이지에 접속할 때 표시할 정보를 제공합니다."
                            + " 배송지의 address는 온보딩에서 유저가 고른 종류(도로명 또는 지번)의 주소입니다. 화면에는 우편번호,"
                            + " 주소, 상세주소를 이어 붙여 보여주면 됩니다.")
    @CustomerOnly
    @GetMapping("/profile")
    public ApiResponse<CustomerProfileResponse> getProfile(@LoginUser AuthUser customer) {
        return ApiResponse.ok(
                userService.getCustomerProfile(customer.userId(), customer.customerId()));
    }
}
