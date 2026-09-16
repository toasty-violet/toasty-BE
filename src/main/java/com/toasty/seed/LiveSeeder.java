package com.toasty.seed;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 목 라이브를 만든다. {@link UserSeeder}가 만든 목 판매자에 붙으므로 그 뒤에 돌아야 한다.
 *
 * <p>홈 라이브 섹션에 방송 중 카드와 예정 카드가 함께 나오도록 두 상태를 섞어 두고, 1번 구매자(토스티)가 팔로우한 1·3번 스토어와 그렇지 않은 2·4번 스토어에 나눠
 * 걸어 팔로우 버튼 상태도 섞여 나오게 한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LiveSeeder {

    // AWS가 공개해 둔 데모 채널. 실제로 송출 중이라 카드 자동 재생까지 확인할 수 있다.
    private static final String DEMO_PLAYBACK_URL =
            "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8";

    // 진짜 채널이 아니라 배치의 송출 상태 조회는 계속 실패한다. 실패한 라이브는 건너뛰므로 상태는 시더가 넣은 값 그대로 남는다.
    private static final String MOCK_CHANNEL_ARN_FORMAT =
            "arn:aws:ivs:us-east-1:000000000000:channel/mock%d";

    private static final String THUMBNAIL_URL_FORMAT = "https://picsum.photos/seed/%s/456/684";

    // 방송 중 카드가 예정 카드보다 앞에 오므로, 시청자 수를 벌려 정렬도 눈으로 확인할 수 있게 한다.
    private static final List<MockBroadcastingLive> BROADCASTING =
            List.of(
                    new MockBroadcastingLive(
                            1,
                            "🍂 가을 코디 창고 대방출!",
                            "가을 신상부터 이월 상품까지 창고를 정리합니다.",
                            132,
                            "toasty-live-1"),
                    new MockBroadcastingLive(
                            2, "오늘 구운 빵 떨이합니다", "마감 전 남은 빵을 반값에 드려요.", 87, "toasty-live-2"));

    // 방송 예정 시각이 지나면 홈에서 빠지므로 넉넉히 앞으로 잡는다.
    private static final List<MockScheduledLive> SCHEDULED =
            List.of(
                    new MockScheduledLive(
                            3,
                            "이번주 신상 입고 라방!!",
                            "이번 주 들어온 신상을 하나씩 보여드릴게요.",
                            2,
                            20,
                            "toasty-live-3"),
                    new MockScheduledLive(
                            4,
                            "주말 한정 베이킹 클래스",
                            "집에서 따라 하는 버터 스콘 레시피를 함께 만듭니다.",
                            5,
                            11,
                            "toasty-live-4"));

    private static final List<LiveStatus> UNFINISHED_STATUSES =
            List.of(LiveStatus.READY, LiveStatus.LIVE);

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final LiveRepository liveRepository;

    /** 끝나지 않은 라이브를 이미 들고 있는 판매자는 건너뛰므로, 서버를 다시 켜도 중복으로 쌓이지 않는다. */
    @Transactional
    public void seed() {
        int created = 0;
        for (MockBroadcastingLive mock : BROADCASTING) {
            created += seedBroadcasting(mock) ? 1 : 0;
        }
        for (MockScheduledLive mock : SCHEDULED) {
            created += seedScheduled(mock) ? 1 : 0;
        }
        log.info("목 라이브 시딩 완료 — 새로 만든 라이브 {}건", created);
    }

    private boolean seedBroadcasting(MockBroadcastingLive mock) {
        Long sellerId = findSeedableSellerId(mock.sellerIndex()).orElse(null);
        if (sellerId == null) {
            return false;
        }
        Live live =
                create(
                        sellerId,
                        mock.sellerIndex(),
                        mock.title(),
                        mock.description(),
                        LocalDateTime.now().minusHours(1),
                        mock.thumbnailSeed());
        live.startBroadcast();
        live.updateViewerCount(mock.viewerCount());
        liveRepository.save(live);
        return true;
    }

    private boolean seedScheduled(MockScheduledLive mock) {
        Long sellerId = findSeedableSellerId(mock.sellerIndex()).orElse(null);
        if (sellerId == null) {
            return false;
        }
        LocalDateTime scheduledAt =
                LocalDate.now().plusDays(mock.daysFromNow()).atTime(mock.hour(), 0);
        liveRepository.save(
                create(
                        sellerId,
                        mock.sellerIndex(),
                        mock.title(),
                        mock.description(),
                        scheduledAt,
                        mock.thumbnailSeed()));
        return true;
    }

    // 채팅방은 IVS Chat을 불러야 만들 수 있어 비워 둔다. 홈 카드에는 쓰이지 않는다.
    private Live create(
            Long sellerId,
            int index,
            String title,
            String description,
            LocalDateTime scheduledAt,
            String thumbnailSeed) {
        Live live =
                Live.create(
                        new LiveCreateCommand(sellerId, title, description, scheduledAt, List.of()),
                        MOCK_CHANNEL_ARN_FORMAT.formatted(index),
                        DEMO_PLAYBACK_URL,
                        null);
        live.updateThumbnailUrl(THUMBNAIL_URL_FORMAT.formatted(thumbnailSeed));
        return live;
    }

    // 목 유저 시딩이 상점명 중복으로 건너뛴 자리와, 이미 라이브를 들고 있는 판매자는 그대로 둔다.
    private Optional<Long> findSeedableSellerId(int index) {
        return userRepository
                .findByKakaoId(UserSeeder.sellerKakaoId(index))
                .flatMap(user -> sellerRepository.findByUserId(user.getId()))
                .map(Seller::getId)
                .filter(this::hasNoUnfinishedLive);
    }

    private boolean hasNoUnfinishedLive(Long sellerId) {
        return liveRepository
                .findBySellerIdAndStatusInOrderByScheduledAtAsc(sellerId, UNFINISHED_STATUSES)
                .isEmpty();
    }

    private record MockBroadcastingLive(
            int sellerIndex,
            String title,
            String description,
            int viewerCount,
            String thumbnailSeed) {}

    private record MockScheduledLive(
            int sellerIndex,
            String title,
            String description,
            int daysFromNow,
            int hour,
            String thumbnailSeed) {}
}
