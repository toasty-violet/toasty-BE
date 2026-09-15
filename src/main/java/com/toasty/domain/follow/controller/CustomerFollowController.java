package com.toasty.domain.follow.controller;

import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.follow.controller.dto.response.FollowingStoreResponse;
import com.toasty.domain.follow.service.FollowService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store Follow", description = "스토어 팔로우 API")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerFollowController {

    private final FollowService followService;

    @Operation(
            summary = "팔로우하는 스토어 조회",
            description =
                    "홈 화면의 팔로우하는 스토어 섹션을 채웁니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다."
                            + " 최근 팔로우한 순으로 최대 3개를 내려주며, 스토어마다 판매중인 상품이 최신순으로 최대 3개"
                            + " 함께 옵니다. 토큰을 보내지 않았거나 구매자가 아니거나 아직 아무도 팔로우하지 않았으면,"
                            + " 섹션이 비지 않도록 먼저 만들어진 스토어 3개로 대신 채워집니다. 판매중인 상품이 없는 스토어도"
                            + " 자리를 지키므로 products는 빈 배열일 수 있습니다.")
    @GetMapping("/following")
    public ApiResponse<List<FollowingStoreResponse>> findFollowingStores(@LoginUser AuthUser user) {
        return ApiResponse.ok(
                followService.findFollowingStores(user == null ? null : user.customerId()));
    }
}
