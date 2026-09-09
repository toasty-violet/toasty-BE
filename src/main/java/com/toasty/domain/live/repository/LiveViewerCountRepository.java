package com.toasty.domain.live.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 시청자 수를 라이브별로 담아둔다. 두 종류의 키를 쓴다.
//   live:viewer-count:{publicId}          → 시청자 수
//   live:viewer-count:refresh:{publicId}  → 갱신 선점. 있으면 누군가 최근에 IVS를 불렀다는 뜻이다.
// 값 키만 두면 만료 순간에 도착한 요청이 전부 IVS를 부른다. 선점 키를 먼저 잡은 하나만 부르고
// 나머지는 직전 값을 그대로 쓴다.
@Repository
@RequiredArgsConstructor
public class LiveViewerCountRepository {

    private static final String VALUE_KEY_PREFIX = "live:viewer-count:";
    private static final String REFRESH_KEY_PREFIX = "live:viewer-count:refresh:";
    // 값은 갱신이 밀려도 화면이 빈 채로 남지 않도록 넉넉히 둔다.
    private static final Duration VALUE_TTL = Duration.ofMinutes(1);
    // 라이브당 IVS를 부르는 간격이다.
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    public Optional<Integer> find(String publicId) {
        String value = redisTemplate.opsForValue().get(VALUE_KEY_PREFIX + publicId);
        return value == null ? Optional.empty() : Optional.of(Integer.valueOf(value));
    }

    public void save(String publicId, int viewerCount) {
        redisTemplate
                .opsForValue()
                .set(VALUE_KEY_PREFIX + publicId, String.valueOf(viewerCount), VALUE_TTL);
    }

    /** 갱신을 선점한 요청 하나에만 참을 준다. 나머지는 직전 값을 그대로 쓴다. */
    public boolean tryStartRefresh(String publicId) {
        return Boolean.TRUE.equals(
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(REFRESH_KEY_PREFIX + publicId, "1", REFRESH_INTERVAL));
    }
}
