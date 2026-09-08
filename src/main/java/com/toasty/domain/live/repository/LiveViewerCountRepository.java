package com.toasty.domain.live.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 시청자 수를 라이브별로 잠깐 담아둔다.
//   live:viewer-count:{publicId} → 시청자 수
// 시청자마다 IVS를 부르면 호출이 시청자 수에 비례한다. TTL 동안은 라이브당 한 번만 부른다.
@Repository
@RequiredArgsConstructor
public class LiveViewerCountRepository {

    private static final String KEY_PREFIX = "live:viewer-count:";
    private static final Duration TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    public Optional<Integer> find(String publicId) {
        String value = redisTemplate.opsForValue().get(key(publicId));
        return value == null ? Optional.empty() : Optional.of(Integer.valueOf(value));
    }

    public void save(String publicId, int viewerCount) {
        redisTemplate.opsForValue().set(key(publicId), String.valueOf(viewerCount), TTL);
    }

    private String key(String publicId) {
        return KEY_PREFIX + publicId;
    }
}
