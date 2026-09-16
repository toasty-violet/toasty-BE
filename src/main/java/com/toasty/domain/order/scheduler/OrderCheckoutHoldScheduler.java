package com.toasty.domain.order.scheduler;

import com.toasty.domain.order.service.OrderService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결제창만 열어 두고 끝내지 않은 주문의 재고 선점을 주기적으로 푼다. */
// 선점이 풀리는 시각보다 자주 돌아, 기다리던 사람이 늦어도 1분 안에는 구매 버튼을 받는다.
@Component
@RequiredArgsConstructor
public class OrderCheckoutHoldScheduler {

    private final OrderService orderService;

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void releaseExpiredCheckoutHolds() {
        orderService.releaseExpiredCheckoutHolds();
    }
}
