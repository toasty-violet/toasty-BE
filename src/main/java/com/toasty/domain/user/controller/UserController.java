package com.toasty.domain.user.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.exception.ErrorResponse;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
                    "로그인한 유저의 정보를 조회합니다. 로그인 직후와 앱 진입 시 호출해 온보딩 여부와 역할에 따라 화면을 분기하세요."
                            + " 온보딩 전이면 nickname은 가입 시 발급된 임시 닉네임이며, 온보딩 입력창의 기본값으로 쓰세요.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "액세스 토큰이 없거나 유효하지 않은 경우",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @LoginRequired
    @GetMapping("/users/me")
    public ApiResponse<UserMeResponse> getMe(@LoginUser AuthUser user) {
        return ApiResponse.ok(userService.getMe(user.userId()));
    }

    @Operation(
            summary = "닉네임 중복 조회",
            description =
                    "유저가 온보딩 중 입력한 닉네임을 다른 유저가 쓰고 있는지 확인합니다. duplicated가 true면 사용할 수 없는 닉네임입니다."
                            + " 토큰을 함께 보내면 자기 닉네임은 중복으로 보지 않아, 가입 시 받은 닉네임을 그대로 두고 진행할 수 있습니다.")
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
