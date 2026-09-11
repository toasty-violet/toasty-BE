package com.toasty.domain.live.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 홈 화면의 라이브 카드 한 장을 채운다. */
// 셀러를 못 찾아도 화면이 카드 자리를 잡도록 null이어도 키를 남긴다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HomeLiveResponse(
        @Schema(description = "외부 공유용 식별자. 시청 화면 진입에 쓴다") String publicId,
        @Schema(description = "READY / LIVE") LiveStatus status,
        String title,
        @Schema(description = "방송 예정 시각. 예정 카드의 배지에 쓴다") LocalDateTime scheduledAt,
        @Schema(description = "IVS Player SDK에 넘길 재생 URL. 방송 중 카드의 썸네일 자동 재생에 쓴다")
                String playbackUrl,
        @Schema(description = "시청자 수. 방송 중이 아니면 0") int viewerCount,
        @Schema(description = "방송을 진행하는 셀러") SellerProfileResponse seller) {

    public static HomeLiveResponse of(Live live, SellerProfileResponse seller) {
        return new HomeLiveResponse(
                live.getPublicId(),
                live.getStatus(),
                live.getTitle(),
                live.getScheduledAt(),
                live.getPlaybackUrl(),
                live.getViewerCount(),
                seller);
    }
}
