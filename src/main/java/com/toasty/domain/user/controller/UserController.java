package com.toasty.domain.user.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.user.controller.dto.response.UserMeResponse;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.exception.ErrorResponse;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "유저 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 유저의 정보를 조회합니다. 로그인 직후와 앱 진입 시 호출해 온보딩 여부와 역할에 따라 화면을 분기하세요.")
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
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@LoginUser AuthUser user) {
        return ApiResponse.ok(userService.getMe(user.userId()));
    }
}
