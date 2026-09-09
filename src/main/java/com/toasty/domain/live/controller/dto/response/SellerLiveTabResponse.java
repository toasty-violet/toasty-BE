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
        @Schema(description = "가장 최근 방송의 집계. 아직 제공하지 않아 항상 null이다") LatestStat latestStat,
        @Schema(description = "지금 방송 중인 라이브. 없으면 null") Broadcasting broadcasting,
        @Schema(description = "예정된 라이브. 방송 예정 시각 오름차순") List<Scheduled> scheduled) {

    public static SellerLiveTabResponse of(Live broadcasting, List<Scheduled> scheduled) {
        return new SellerLiveTabResponse(
                null, broadcasting == null ? null : Broadcasting.from(broadcasting), scheduled);
    }

    /** 시청자 수·주문 수·판매 금액. 주문과 시청자 집계가 생기면 채운다. */
    public record LatestStat(
            @Schema(description = "시청자 수") int viewerCount,
            @Schema(description = "주문 수") int orderCount,
            @Schema(description = "판매 금액(원)") long salesAmount) {}

    public record Broadcasting(
            @Schema(description = "라이브 번호") Long liveId,
            @Schema(description = "외부 공유용 식별자. 링크 복사·방송 보기에 쓴다") String publicId,
            String title,
            @Schema(description = "IVS Player SDK에 넘길 재생 URL") String playbackUrl,
            @Schema(description = "판매율(%). 판매 수량 / 총 재고") int sellThroughRate) {

        // 판매 수량을 알 수 있는 곳이 아직 없어 분자가 0이다. 분모를 구해도 결과가 0이라 재고 합은 조회하지 않는다.
        private static final int SELL_THROUGH_RATE_WITHOUT_ORDERS = 0;

        public static Broadcasting from(Live live) {
            return new Broadcasting(
                    live.getId(),
                    live.getPublicId(),
                    live.getTitle(),
                    live.getPlaybackUrl(),
                    SELL_THROUGH_RATE_WITHOUT_ORDERS);
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
