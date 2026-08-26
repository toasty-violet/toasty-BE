package com.toasty.domain.live.controller;

import com.toasty.domain.live.controller.dto.request.LiveCreateRequest;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveCreateResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.service.LiveService;
import com.toasty.global.auth.SellerProvider;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Live", description = "라이브 방송 API")
@RestController
@RequestMapping("/api/v1/lives")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService liveService;
    private final SellerProvider sellerProvider;

    @Operation(
            summary = "라이브 생성",
            description =
                    "셀러가 새 라이브를 개설하고, 방송 송출에 필요한 정보를 발급받습니다. 최초 송출정보는 이 응답에서만 전달되며, 이후에는 재발급 API로만"
                            + " 받을 수 있습니다.")
    @PostMapping
    public ApiResponse<LiveCreateResponse> create(@Valid @RequestBody LiveCreateRequest request) {
        Long sellerId = sellerProvider.currentSellerId();
        return ApiResponse.ok(liveService.create(request.toCommand(sellerId)));
    }

    @Operation(
            summary = "송출정보 재발급",
            description = "기존 스트림 키를 폐기하고 새로 발급한다. 이 응답에서만 전달되며 다시 조회할 수 없다.")
    @PostMapping("/{liveId}/broadcast-credentials")
    public ApiResponse<BroadcastCredentialResponse> reissueCredential(@PathVariable Long liveId) {
        Long sellerId = sellerProvider.currentSellerId();
        return ApiResponse.ok(liveService.reissueCredential(liveId, sellerId));
    }

    @Operation(
            summary = "라이브 시청",
            description =
                    "유저가 해당 id에 해당하는 라이브를 시청합니다. 재생에 필요한 playbackUrl과 라이브 정보를 반환하며, 송출정보(streamKey,"
                            + " ingestEndpoint)는 포함하지 않습니다.")
    @GetMapping("/{liveId}")
    public ApiResponse<LiveDetailResponse> getById(@PathVariable Long liveId) {
        return ApiResponse.ok(liveService.getById(liveId));
    }
}
