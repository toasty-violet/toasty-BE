package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 공용 응답. streamKey·streamKeyArn·ingestEndpoint를 절대 포함하지 않는다. */
public record LiveDetailResponse(
        @Schema(description = "라이브 번호") Long liveId,
        @Schema(description = "외부 공유용 식별자") String publicId,
        @Schema(description = "셀러 번호") Long sellerId,
        String title,
        String description,
        @Schema(description = "방송 예정 시각") LocalDateTime scheduledAt,
        @Schema(description = "READY / LIVE / ENDED") LiveStatus status,
        @Schema(description = "IVS Player SDK에 넘길 재생 URL") String playbackUrl,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt) {

    public static LiveDetailResponse from(Live live) {
        return new LiveDetailResponse(
                live.getId(),
                live.getPublicId(),
                live.getSellerId(),
                live.getTitle(),
                live.getDescription(),
                live.getScheduledAt(),
                live.getStatus(),
                live.getPlaybackUrl(),
                live.getStartedAt(),
                live.getEndedAt(),
                live.getCreatedAt());
    }
}
