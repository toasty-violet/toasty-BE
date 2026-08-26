package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record LiveStreamStatusResponse(
        @Schema(description = "READY / LIVE / ENDED") LiveStatus status,
        @Schema(description = "IVS가 실제로 송출을 받고 있는지") boolean broadcasting,
        LocalDateTime startedAt) {

    public static LiveStreamStatusResponse of(Live live, StreamState streamState) {
        return new LiveStreamStatusResponse(
                live.getStatus(), streamState == StreamState.BROADCASTING, live.getStartedAt());
    }
}
