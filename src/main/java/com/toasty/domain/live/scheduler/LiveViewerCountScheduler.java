package com.toasty.domain.live.scheduler;

import com.toasty.domain.live.service.LiveService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 방송 중인 라이브의 시청자 수를 주기적으로 갱신한다. */
// 요청이 올 때마다 IVS를 부르면 호출이 시청자 수에 비례한다. 여기서만 불러 방송 수에 비례하게 묶는다.
@Component
@RequiredArgsConstructor
public class LiveViewerCountScheduler {

    private final LiveService liveService;

    // 기동 직후에는 돌지 않는다. 테스트 컨텍스트에서 AWS를 건드리지 않게 한다.
    @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void refreshViewerCounts() {
        liveService.refreshViewerCounts();
    }
}
