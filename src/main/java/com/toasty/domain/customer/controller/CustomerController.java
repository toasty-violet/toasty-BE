package com.toasty.domain.customer.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.controller.dto.request.CustomerProfileUpdateRequest;
import com.toasty.domain.customer.controller.dto.response.CustomerProfileResponse;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                    """
                    구매자가 마이페이지에 접속할 때 표시할 정보를 제공합니다.
                    배송지는 저장된 값을 그대로 내려주므로 내 정보 수정 요청에 그대로 반영할 수 있습니다.
                    """)
    @CustomerOnly
    @GetMapping("/profile")
    public ApiResponse<CustomerProfileResponse> getProfile(@LoginUser AuthUser customer) {
        return ApiResponse.ok(
                userService.getCustomerProfile(customer.userId(), customer.customerId()));
    }

    @Operation(
            summary = "구매자 내 정보 수정",
            description =
                    """
                    구매자의 이름, 닉네임, 연락처, 기본 배송지를 수정합니다.
                    내 정보 조회 응답을 입력창의 기본값으로 채워 두고, 유저가 수정한 상태를 보내면 됩니다.
                    바뀐 값만 골라 보낼 수는 없고, 보낸 값이 그대로 저장됩니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "수정 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "입력값이 올바르지 않은 경우 — 어느 필드가 틀렸는지는 error.fields에 담기므로 해당 입력창 아래에 띄우세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "COMMON_INVALID_INPUT",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "COMMON_INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "fields": [{"field": "phoneNumber", "message": "휴대폰 번호 형식이 올바르지 않습니다."}]}}
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "액세스 토큰이 없거나 유효하지 않은 경우 — 로그인 화면으로 보내세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "COMMON_UNAUTHORIZED",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "COMMON_UNAUTHORIZED", "message": "인증이 필요합니다."}}
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "구매자가 아닌 유저가 호출한 경우",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "COMMON_FORBIDDEN",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "COMMON_FORBIDDEN", "message": "접근 권한이 없습니다."}}
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "토큰은 유효하지만 그 사이 탈퇴한 유저인 경우 — 로그인 화면으로 보내세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "USER_NOT_FOUND",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "USER_NOT_FOUND", "message": "존재하지 않는 유저입니다."}}
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "다른 유저가 이미 쓰고 있는 닉네임 — 닉네임 입력창 아래에 띄우세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "USER_NICKNAME_DUPLICATED",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "USER_NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다."}}
                                                        """)))
    })
    @CustomerOnly
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(
            @Valid @RequestBody CustomerProfileUpdateRequest request,
            @LoginUser AuthUser customer) {
        userService.updateCustomerProfile(
                request.toCommand(customer.userId(), customer.customerId()));
        return ApiResponse.ok();
    }
}
