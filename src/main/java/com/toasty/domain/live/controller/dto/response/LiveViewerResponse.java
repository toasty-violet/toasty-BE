package com.toasty.domain.live.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 시청 화면 진입에 필요한 정보. 송출정보(streamKey, ingestEndpoint)를 절대 포함하지 않는다. */
// 방송 전에는 startedAt이 없어 키가 빠지는데, 그때도 화면이 대기 상태를 그려야 한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LiveViewerResponse(
        @Schema(description = "라이브 번호") Long liveId,
        @Schema(description = "외부 공유용 식별자") String publicId,
        String title,
        String description,
        @Schema(description = "READY / LIVE / ENDED") LiveStatus status,
        @Schema(description = "IVS Player SDK에 넘길 재생 URL") String playbackUrl,
        @Schema(description = "방송 예정 시각") LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        @Schema(description = "방송을 진행하는 셀러") SellerProfileResponse seller) {

    public static LiveViewerResponse of(Live live, SellerProfileResponse seller) {
        return new LiveViewerResponse(
                live.getId(),
                live.getPublicId(),
                live.getTitle(),
                live.getDescription(),
                live.getStatus(),
                live.getPlaybackUrl(),
                live.getScheduledAt(),
                live.getStartedAt(),
                live.getEndedAt(),
                seller);
    }
}
