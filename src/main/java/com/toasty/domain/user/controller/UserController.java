package com.toasty.domain.user.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.controller.dto.request.CustomerOnboardingRequest;
import com.toasty.domain.user.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.service.UserService;
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

@Tag(name = "User", description = "유저 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description =
                    """
                    로그인한 유저의 정보를 조회합니다.
                    role이 null이면 온보딩 전이므로 역할 선택 화면으로 보내고, 값이 있으면 그 역할에 맞는 화면으로 보냅니다.
                    온보딩 전이면 nickname은 가입 시 발급된 임시 닉네임이며, 온보딩 입력창의 기본값으로 쓰세요.
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
    @GetMapping("/users/me")
    public ApiResponse<UserMeResponse> getMe(@LoginUser AuthUser user) {
        return ApiResponse.ok(userService.getMe(user.userId()));
    }

    @Operation(
            summary = "구매자 온보딩",
            description =
                    """
                    유저의 역할을 CUSTOMER로 확정하고 닉네임, 전화번호, 배송지를 입력합니다.
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
                        "error.code로 갈라 처리하세요. 닉네임 중복은 입력창에, 온보딩 중복은 내 정보 조회로 되돌려 화면을 다시 분기하세요",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = {
                                    @ExampleObject(
                                            name = "USER_NICKNAME_DUPLICATED",
                                            description = "다른 유저가 이미 쓰고 있는 닉네임",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "USER_NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다."}}
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
            summary = "닉네임 중복 조회",
            description =
                    """
                    유저가 온보딩 중 입력한 닉네임을 다른 유저가 쓰고 있는지 확인합니다.
                    duplicated가 true면 사용할 수 없는 닉네임입니다.
                    토큰을 함께 보내면 자기 닉네임은 중복으로 보지 않아, 가입 시 받은 닉네임을 그대로 두고 진행할 수 있습니다.
                    """)
    @GetMapping("/search-nickname")
    public ApiResponse<NicknameSearchResponse> searchNickname(
            @Parameter(description = "조회할 닉네임", required = true, example = "토스티")
                    @RequestParam
                    @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 20, message = "닉네임은 20자를 넘을 수 없습니다.") String nickname,
            @LoginUser AuthUser user) {
        return ApiResponse.ok(
                userService.searchNickname(nickname, user == null ? null : user.userId()));
    }
}
