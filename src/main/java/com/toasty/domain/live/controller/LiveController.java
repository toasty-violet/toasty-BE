package com.toasty.domain.live.controller;

import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.annotation.SellerOnly;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.live.controller.dto.request.LiveCreateRequest;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveCreateResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.service.LiveService;
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

    @Operation(
            summary = "라이브 생성",
            description =
                    "셀러가 새 라이브를 개설하고, 방송 송출에 필요한 정보를 발급받습니다. 최초 송출정보는 이 응답에서만 전달되며, 이후에는 재발급 API로만"
                            + " 받을 수 있습니다.")
    @SellerOnly
    @PostMapping
    public ApiResponse<LiveCreateResponse> create(
            @Valid @RequestBody LiveCreateRequest request, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.create(request.toCommand(seller.userId())));
    }

    @Operation(
            summary = "송출정보 재발급",
            description =
                    "셀러가 방송 송출에 필요한 정보를 새로 발급받습니다. 송출 직전에 호출하세요. 기존 스트림 키는 즉시 폐기되며, 새 송출정보는 이 응답에서만"
                            + " 전달되어 다시 조회할 수 없습니다.")
    @SellerOnly
    @PostMapping("/{liveId}/broadcast-credentials")
    public ApiResponse<BroadcastCredentialResponse> reissueCredential(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.reissueCredential(liveId, seller.userId()));
    }

    @Operation(
            summary = "방송 종료",
            description =
                    "셀러가 진행 중인 라이브를 종료합니다. 송출이 중단되고 스트림 키가 삭제되어 다시 송출할 수 없으며, 채널과 재생 URL은 지난 방송"
                            + " 페이지를 위해 유지됩니다. 본인의 라이브만 종료할 수 있습니다.")
    @SellerOnly
    @PostMapping("/{liveId}/end")
    public ApiResponse<LiveDetailResponse> end(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.end(liveId, seller.userId()));
    }

    @Operation(
            summary = "송출 상태 조회",
            description =
                    "셀러가 자신의 송출이 실제로 시작됐는지 확인합니다. 셀러의 송출 대기 화면에서만 폴링하세요. 요청마다 IVS를 호출하므로 시청자 화면에서"
                        + " 쓰면 시청자 수만큼 호출이 늘어납니다(시청자에게는 재생 정보 조회 API를 쓰세요). IVS의 실제 송출 여부를 조회해 라이브"
                        + " 상태를 맞추며, 송출이 확인되면 LIVE로 전이됩니다. 본인의 라이브만 조회할 수 있습니다.")
    @SellerOnly
    @GetMapping("/{liveId}/stream-status")
    public ApiResponse<LiveStreamStatusResponse> getStreamStatus(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.getStreamStatus(liveId, seller.userId()));
    }

    @Operation(
            summary = "재생 정보 조회",
            description =
                    "시청자가 방송이 시작됐는지 확인하고 재생을 시작합니다. 시청자 대기 화면에서 이 API를 폴링하고, status가 LIVE가 되면"
                            + " playbackUrl로 재생을 시작하세요. 경로의 publicId는 시청 화면 진입에 쓴 값을 그대로 사용합니다."
                            + " 저장된 상태만 읽어 IVS를 호출하지 않으므로 시청자가 많아도 부담이 없으며, 송출정보(streamKey,"
                            + " ingestEndpoint)는 포함하지 않습니다.")
    @GetMapping("/public/{publicId}/playback")
    public ApiResponse<LivePlaybackResponse> getPlayback(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getPlayback(publicId));
    }

    @Operation(
            summary = "라이브 시청",
            description =
                    "유저가 라이브 시청 화면에 들어올 때 필요한 정보를 가져옵니다. 시청 화면 진입 시 한 번 호출하세요. 인증이 필요 없어"
                            + " 비로그인 유저도 호출할 수 있습니다. 경로의 publicId는 라이브 생성 응답으로 받은 값이며, 순차 liveId를"
                            + " 시청 화면 URL에 노출하지 않기 위해 공개 조회는 이 값만 받습니다. 재생에 필요한 playbackUrl과 라이브"
                            + " 정보를 반환하며, 송출정보(streamKey, ingestEndpoint)는 포함하지 않습니다.")
    @GetMapping("/public/{publicId}")
    public ApiResponse<LiveDetailResponse> getByPublicId(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getByPublicId(publicId));
    }
}
