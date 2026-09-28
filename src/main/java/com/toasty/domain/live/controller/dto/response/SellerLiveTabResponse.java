package com.toasty.domain.live.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.live.entity.Live;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 셀러 라이브탭 한 화면을 채운다. 세 필드가 화면의 세 구역에 그대로 대응한다. */
// 값이 없을 때 화면이 빈 상태를 그려야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerLiveTabResponse(
        @Schema(description = "가장 최근에 끝난 방송의 집계. 끝낸 방송이 없으면 null") LatestStat latestStat,
        @Schema(description = "지금 방송 중인 라이브. 없으면 null") Broadcasting broadcasting,
        @Schema(description = "예정된 라이브. 방송 예정 시각 오름차순") List<Scheduled> scheduled) {

    public static SellerLiveTabResponse of(
            LatestStat latestStat,
            Live broadcasting,
            int sellThroughRate,
            List<Scheduled> scheduled) {
        return new SellerLiveTabResponse(
                latestStat,
                broadcasting == null ? null : Broadcasting.of(broadcasting, sellThroughRate),
                scheduled);
    }

    /** 시청자 수·주문 수·판매 금액. 시청자 수는 그 방송의 최고 동시 시청자다. */
    public record LatestStat(
            @Schema(description = "시청자 수") int viewerCount,
            @Schema(description = "주문 수") long orderCount,
            @Schema(description = "판매 금액(원). 배송비를 뺀 상품 금액이다") long salesAmount) {}

    public record Broadcasting(
            @Schema(description = "라이브 번호") Long liveId,
            @Schema(description = "외부 공유용 식별자. 링크 복사·방송 보기에 쓴다") String publicId,
            String title,
            @Schema(description = "IVS Player SDK에 넘길 재생 URL") String playbackUrl,
            @Schema(description = "판매율(%). 이 방송에서 팔린 수량 / (남은 재고 + 팔린 수량)") int sellThroughRate) {

        public static Broadcasting of(Live live, int sellThroughRate) {
            return new Broadcasting(
                    live.getId(),
                    live.getPublicId(),
                    live.getTitle(),
                    live.getPlaybackUrl(),
                    sellThroughRate);
        }
    }

    public record Scheduled(
            @Schema(description = "라이브 번호") Long liveId,
            @Schema(description = "외부 공유용 식별자. 링크 복사에 쓴다") String publicId,
            String title,
            @Schema(description = "방송 예정 시각") LocalDateTime scheduledAt,
            @Schema(description = "편성된 상품 수") int productCount) {

        public static Scheduled of(Live live, int productCount) {
            return new Scheduled(
                    live.getId(),
                    live.getPublicId(),
                    live.getTitle(),
                    live.getScheduledAt(),
                    productCount);
        }
    }
}
