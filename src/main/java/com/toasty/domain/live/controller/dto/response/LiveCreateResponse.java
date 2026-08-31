package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.entity.Live;
import io.swagger.v3.oas.annotations.media.Schema;

/** 생성 직후에만 송출정보를 함께 준다. 이후에는 재발급 API로만 받을 수 있다. */
public record LiveCreateResponse(
        @Schema(description = "생성된 라이브 정보") LiveDetailResponse live,
        @Schema(description = "최초 송출정보. 이 응답에서만 전달된다.")
                BroadcastCredentialResponse broadcastCredential) {

    public static LiveCreateResponse of(Live live, BroadcastCredential credential) {
        return new LiveCreateResponse(
                LiveDetailResponse.from(live), BroadcastCredentialResponse.from(credential));
    }
}
