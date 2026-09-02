package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record LivePlaybackResponse(
        @Schema(description = "IVS Player SDK에 넘길 재생 URL") String playbackUrl,
        @Schema(description = "READY / LIVE / ENDED") LiveStatus status) {

    public static LivePlaybackResponse from(Live live) {
        return new LivePlaybackResponse(live.getPlaybackUrl(), live.getStatus());
    }
}
