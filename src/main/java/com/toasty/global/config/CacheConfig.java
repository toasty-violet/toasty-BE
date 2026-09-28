package com.toasty.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** 라이브 시청 화면이 시청자 수만큼 같은 값을 다시 읽지 않도록 짧게 캐시한다. */
// 만료 시간은 application.yml의 caffeine spec이 정한다. 시청 화면 폴링 주기보다 짧게 잡는다.
@Configuration
@EnableCaching
public class CacheConfig {

    /** 라이브 시청 화면이 주기적으로 읽는 값. 모든 시청자에게 같은 값이라 사람마다 나누지 않는다. */
    public static final String PUBLIC_LIVE = "publicLive";
}
