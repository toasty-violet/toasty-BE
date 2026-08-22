package com.toasty.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// Redis에 리프레시 토큰 저장
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    // userId 기준으로 리프레시 토큰을 TTL과 함께 저장
    public void save(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, refreshToken, ttl);
    }

    // 해당 사용자가 지금 로그인된 상태인지, 저장된 리프레시 토큰을 찾아온다
    public Optional<String> findByUserId(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    // 로그아웃 처리 — 해당 사용자의 리프레시 토큰을 저장소에서 지운다
    public void deleteByUserId(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
