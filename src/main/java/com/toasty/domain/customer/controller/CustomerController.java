package com.toasty.domain.customer.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.controller.dto.request.CustomerProfileUpdateRequest;
import com.toasty.domain.customer.controller.dto.response.CustomerProfileResponse;
import com.toasty.domain.customer.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.customer.controller.dto.response.NicknameSuggestionResponse;
import com.toasty.domain.customer.service.CustomerService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer", description = "구매자 API")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

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
        return ApiResponse.ok(customerService.getProfile(customer.customerId()));
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
                                                name = "CUSTOMER_NOT_FOUND",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "CUSTOMER_NOT_FOUND", "message": "존재하지 않는 구매자입니다."}}
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "다른 구매자가 이미 쓰고 있는 닉네임 — 닉네임 입력창 아래에 띄우세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "CUSTOMER_NICKNAME_DUPLICATED",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "CUSTOMER_NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다."}}
                                                        """)))
    })
    @CustomerOnly
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(
            @Valid @RequestBody CustomerProfileUpdateRequest request,
            @LoginUser AuthUser customer) {
        customerService.updateProfile(request.toCommand(customer.customerId()));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "닉네임 중복 조회",
            description =
                    """
                    입력한 닉네임을 다른 구매자가 쓰고 있는지 확인합니다.
                    duplicated가 true면 사용할 수 없는 닉네임입니다.
                    판매자의 스토어 이름과는 이름 공간이 달라, 같은 값을 쓰는 스토어가 있어도 중복으로 보지 않습니다.
                    토큰을 함께 보내면 자기 닉네임은 중복으로 보지 않아, 내 정보 수정에서 닉네임을 그대로 두고 진행할 수 있습니다.
                    """)
    @GetMapping("/nickname")
    public ApiResponse<NicknameSearchResponse> searchNickname(
            @Parameter(description = "조회할 닉네임", required = true, example = "토스티")
                    @RequestParam
                    @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 20, message = "닉네임은 20자를 넘을 수 없습니다.") String nickname,
            @LoginUser AuthUser user) {
        return ApiResponse.ok(
                customerService.searchNickname(nickname, user == null ? null : user.customerId()));
    }

    @Operation(
            summary = "추천 닉네임 발급",
            description =
                    """
                    온보딩 화면의 닉네임 입력창에 채워 둘 값을 받습니다. 유저가 그대로 써도 되고 지우고 새로 입력해도 됩니다.
                    발급만 하고 자리를 잡아두지는 않아, 제출 전에 다른 구매자가 먼저 쓸 수 있습니다.
                    그 경우 온보딩 제출이 CUSTOMER_NICKNAME_DUPLICATED로 실패하므로 다시 발급받게 하세요.
                    """)
    @LoginRequired
    @GetMapping("/nickname/suggestion")
    public ApiResponse<NicknameSuggestionResponse> suggestNickname() {
        return ApiResponse.ok(customerService.suggestNickname());
    }
}
