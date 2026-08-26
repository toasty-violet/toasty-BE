package com.toasty.domain.live.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LiveTest {

    private static final Long SELLER_ID = 7L;

    private static Live live() {
        return Live.create(
                new LiveCreateCommand(SELLER_ID, "빈티지 여름옷 라이브", "여름 상품을 소개합니다"),
                "arn:aws:ivs:channel/abc",
                "https://playback/abc.m3u8");
    }

    @Nested
    @DisplayName("방송 시작")
    class StartBroadcast {

        @Test
        @DisplayName("READY에서 시작하면 LIVE가 되고 시작 시각과 활성 셀러가 기록된다")
        void READY에서_시작하면_LIVE가_된다() {
            Live live = live();

            live.startBroadcast();

            assertThat(live.getStatus()).isEqualTo(LiveStatus.LIVE);
            assertThat(live.getStartedAt()).isNotNull();
            assertThat(live.getActiveSellerId()).isEqualTo(SELLER_ID);
        }

        @Test
        @DisplayName("이미 LIVE면 다시 시작해도 시작 시각이 바뀌지 않는다")
        void 이미_LIVE면_시작_시각이_유지된다() {
            Live live = live();
            live.startBroadcast();
            LocalDateTime firstStartedAt = live.getStartedAt();

            live.startBroadcast();

            assertThat(live.getStartedAt()).isEqualTo(firstStartedAt);
            assertThat(live.getStatus()).isEqualTo(LiveStatus.LIVE);
        }

        @Test
        @DisplayName("종료된 라이브는 다시 시작할 수 없다")
        void 종료된_라이브는_시작할_수_없다() {
            Live live = live();
            live.end();

            assertThatThrownBy(live::startBroadcast)
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_ALREADY_ENDED);
        }
    }

    @Nested
    @DisplayName("방송 종료")
    class End {

        @Test
        @DisplayName("LIVE에서 종료하면 ENDED가 되고 활성 셀러가 해제된다")
        void LIVE에서_종료하면_활성_셀러가_해제된다() {
            Live live = live();
            live.startBroadcast();

            live.end();

            assertThat(live.getStatus()).isEqualTo(LiveStatus.ENDED);
            assertThat(live.getEndedAt()).isNotNull();
            assertThat(live.getActiveSellerId()).isNull();
        }

        @Test
        @DisplayName("송출을 시작하지 않은 라이브도 종료할 수 있다")
        void READY에서도_종료할_수_있다() {
            Live live = live();

            live.end();

            assertThat(live.getStatus()).isEqualTo(LiveStatus.ENDED);
            assertThat(live.getEndedAt()).isNotNull();
            assertThat(live.getStartedAt()).isNull();
        }

        @Test
        @DisplayName("이미 종료됐으면 다시 종료해도 종료 시각이 바뀌지 않는다")
        void 이미_종료됐으면_종료_시각이_유지된다() {
            Live live = live();
            live.end();
            LocalDateTime firstEndedAt = live.getEndedAt();

            live.end();

            assertThat(live.getEndedAt()).isEqualTo(firstEndedAt);
            assertThat(live.getStatus()).isEqualTo(LiveStatus.ENDED);
        }
    }
}
