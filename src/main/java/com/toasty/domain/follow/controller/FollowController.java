package com.toasty.domain.follow.controller;

import com.toasty.domain.auth.annotation.CustomerOnly;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.follow.controller.dto.response.TopStoreResponse;
import com.toasty.domain.follow.service.FollowService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store Follow", description = "스토어 팔로우 API")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(
            summary = "인기 스토어 Top3 조회",
            description =
                    "홈 화면의 인기 스토어 섹션을 채웁니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다."
                            + " 팔로워가 많은 순으로 최대 3개를 내려주며, 카드마다 팔로우 버튼의 초기 상태로 쓸"
                            + " following이 함께 옵니다. 토큰을 보내지 않았거나 구매자가 아니면 following은 항상"
                            + " false입니다. 아직 아무도 팔로우하지 않은 스토어는 담기지 않아, 서비스 초기에는 3개보다"
                            + " 적게 오거나 빈 배열일 수 있습니다.")
    @GetMapping("/top")
    public ApiResponse<List<TopStoreResponse>> findTopStores(@LoginUser AuthUser user) {
        return ApiResponse.ok(followService.findTopStores(user == null ? null : user.customerId()));
    }

    @Operation(
            summary = "스토어 팔로우",
            description =
                    "구매자가 스토어를 팔로우합니다. 팔로우 버튼을 누를 때 호출하세요. 이미 팔로우 중인 스토어를 다시"
                            + " 눌러도 성공으로 응답하므로, 연타나 재시도에 실패 처리를 따로 하지 않아도 됩니다.")
    @CustomerOnly
    @PostMapping("/{sellerId}/follow")
    public ApiResponse<Void> follow(
            @Parameter(description = "팔로우할 스토어의 셀러 번호", required = true) @PathVariable
                    Long sellerId,
            @LoginUser AuthUser customer) {
        followService.follow(customer.customerId(), sellerId);
        return ApiResponse.ok();
    }

    @Operation(
            summary = "스토어 팔로우 취소",
            description =
                    "구매자가 스토어 팔로우를 취소합니다. 팔로우 중인 버튼을 다시 누를 때 호출하세요. 팔로우 중이 아닌"
                            + " 스토어에 보내도 성공으로 응답합니다.")
    @CustomerOnly
    @DeleteMapping("/{sellerId}/follow")
    public ApiResponse<Void> unfollow(
            @Parameter(description = "팔로우를 취소할 스토어의 셀러 번호", required = true) @PathVariable
                    Long sellerId,
            @LoginUser AuthUser customer) {
        followService.unfollow(customer.customerId(), sellerId);
        return ApiResponse.ok();
    }
}
