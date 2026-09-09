package com.toasty.domain.user.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.controller.dto.request.CustomerOnboardingRequest;
import com.toasty.domain.user.controller.dto.request.SellerOnboardingRequest;
import com.toasty.domain.user.controller.dto.response.UserRoleResponse;
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

@Tag(name = "User", description = "유저 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 역할 조회",
            description =
                    """
                    로그인한 유저의 역할을 조회합니다.
                    role이 null이면 온보딩 전이므로 역할 선택 화면으로 보내고, 값이 있으면 그 역할에 맞는 화면으로 보냅니다.
                    닉네임과 스토어 이름은 역할이 정해진 뒤에 생기므로 여기에 담기지 않습니다.
                    구매자 닉네임은 구매자 내 정보 조회로, 스토어 이름은 스토어 조회로 받으세요.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "액세스 토큰이 없거나 유효하지 않은 경우",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "COMMON_UNAUTHORIZED",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "COMMON_UNAUTHORIZED", "message": "인증이 필요합니다."}}
                                                        """)))
    })
    @LoginRequired
    @GetMapping("/users/role")
    public ApiResponse<UserRoleResponse> getRole(@LoginUser AuthUser user) {
        return ApiResponse.ok(userService.getRole(user.userId()));
    }

    @Operation(
            summary = "구매자 온보딩",
            description =
                    """
                    유저의 역할을 CUSTOMER로 확정하고 닉네임, 전화번호, 배송지를 입력합니다.
                    닉네임은 구매자끼리만 겹치지 않으면 되므로 닉네임 중복 조회로 미리 확인하세요.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "온보딩 완료"),
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
                description =
                        "error.code로 갈라 처리하세요. 닉네임 중복은 입력창에, 온보딩 중복은 내 역할 조회로 되돌려 화면을 다시 분기하세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = {
                                    @ExampleObject(
                                            name = "CUSTOMER_NICKNAME_DUPLICATED",
                                            description = "다른 구매자가 이미 쓰고 있는 닉네임",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "CUSTOMER_NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다."}}
                                                    """),
                                    @ExampleObject(
                                            name = "USER_ONBOARDING_ALREADY_COMPLETED",
                                            description = "이미 역할이 정해진 유저가 다시 제출",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "USER_ONBOARDING_ALREADY_COMPLETED", "message": "이미 온보딩을 마친 유저입니다."}}
                                                    """)
                                }))
    })
    @LoginRequired
    @PutMapping("/users/onboarding/customer")
    public ApiResponse<Void> completeCustomerOnboarding(
            @Valid @RequestBody CustomerOnboardingRequest request, @LoginUser AuthUser user) {
        userService.completeCustomerOnboarding(request.toCommand(user.userId()));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "판매자 온보딩",
            description =
                    """
                    유저의 역할을 SELLER로 확정하고 스토어 정보, 대표자 정보, 정산 계좌를 입력합니다.
                    스토어 이름은 판매자끼리만 겹치지 않으면 되므로 스토어 이름 중복 조회로 미리 확인하세요.
                    스토어 이미지는 샵 이미지 업로드 주소 발급으로 먼저 올린 뒤 받은 objectKey를 넣습니다.
                    사업자등록번호만 선택 입력이고 나머지는 모두 필수입니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "온보딩 완료"),
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
                                                        {"success": false, "error": {"code": "COMMON_INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "fields": [{"field": "businessNumber", "message": "사업자등록번호는 하이픈 없이 10자리 숫자여야 합니다."}]}}
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
                description = "본인이 발급받지 않은 objectKey를 보낸 경우 — 사진을 다시 올리게 하세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                name = "SELLER_SHOP_IMAGE_FORBIDDEN",
                                                value =
                                                        """
                                                        {"success": false, "error": {"code": "SELLER_SHOP_IMAGE_FORBIDDEN", "message": "본인이 올린 사진이 아닙니다."}}
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
                description =
                        "error.code로 갈라 처리하세요. 스토어 이름·사업자등록번호 중복은 입력창에, 온보딩 중복은 내 역할 조회로 되돌려 화면을 다시"
                                + " 분기하세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = {
                                    @ExampleObject(
                                            name = "SELLER_SHOP_NAME_DUPLICATED",
                                            description = "다른 판매자가 이미 쓰고 있는 스토어 이름",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "SELLER_SHOP_NAME_DUPLICATED", "message": "이미 사용 중인 스토어 이름입니다."}}
                                                    """),
                                    @ExampleObject(
                                            name = "SELLER_BUSINESS_NUMBER_DUPLICATED",
                                            description = "다른 판매자가 이미 등록한 사업자등록번호",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "SELLER_BUSINESS_NUMBER_DUPLICATED", "message": "이미 등록된 사업자등록번호입니다."}}
                                                    """),
                                    @ExampleObject(
                                            name = "USER_ONBOARDING_ALREADY_COMPLETED",
                                            description = "이미 역할이 정해진 유저가 다시 제출",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "USER_ONBOARDING_ALREADY_COMPLETED", "message": "이미 온보딩을 마친 유저입니다."}}
                                                    """)
                                }))
    })
    @LoginRequired
    @PutMapping("/users/onboarding/seller")
    public ApiResponse<Void> completeSellerOnboarding(
            @Valid @RequestBody SellerOnboardingRequest request, @LoginUser AuthUser user) {
        userService.completeSellerOnboarding(request.toCommand(user.userId()));
        return ApiResponse.ok();
    }
}
