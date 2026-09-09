package com.toasty.domain.live.scheduler;

import com.toasty.domain.live.service.LiveService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 종료된 라이브의 채팅방을 주기적으로 회수한다. */
// 서버가 여러 대가 되면 같은 방을 두 인스턴스가 지우려 할 수 있는데, 이미 없는 방 삭제는 통과하므로 안전하다.
@Component
@RequiredArgsConstructor
public class LiveChatRoomCleanupScheduler {

    private final LiveService liveService;

    // 기동 직후에는 돌지 않는다. 배포 직후 부하를 피하고, 테스트 컨텍스트에서 AWS를 건드리지 않게 한다.
    @Scheduled(initialDelay = 10, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void cleanUpEndedChatRooms() {
        liveService.cleanUpEndedChatRooms();
    }
}
