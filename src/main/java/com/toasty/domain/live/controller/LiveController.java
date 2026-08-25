package com.toasty.domain.live.controller;

import com.toasty.domain.live.controller.dto.request.LiveCreateRequest;
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
@RequestMapping("/api/lives")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService liveService;
    private final SellerProvider sellerProvider;

    @Operation(
            summary = "라이브 생성",
            description =
                    "IVS 채널을 만들고 라이브를 READY로 저장한다. 최초 송출정보는 이 응답에서만 전달되며, 이후에는 재발급 API로만 받을 수 있다.")
    @PostMapping
    public ApiResponse<LiveCreateResponse> create(@Valid @RequestBody LiveCreateRequest request) {
        Long sellerId = sellerProvider.currentSellerId();
        return ApiResponse.ok(liveService.create(request.toCommand(sellerId)));
    }

    @Operation(summary = "라이브 상세 조회", description = "송출정보(streamKey, ingestEndpoint)는 포함하지 않는다.")
    @GetMapping("/{liveId}")
    public ApiResponse<LiveDetailResponse> getById(@PathVariable Long liveId) {
        return ApiResponse.ok(liveService.getById(liveId));
    }
}
