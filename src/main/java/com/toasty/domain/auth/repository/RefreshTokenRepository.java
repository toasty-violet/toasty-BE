package com.toasty.domain.auth.repository;

import com.toasty.domain.auth.entity.RefreshToken;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// Redis에 리프레시 토큰을 저장한다. 두 종류의 키를 쓴다.
//   refresh:token:{토큰} → 소유자 userId (회전으로 소비된 뒤에는 "used:{userId}")
//   refresh:user:{userId} → 그 사용자에게 발급된 토큰 목록. 일괄 삭제에 쓴다.
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_KEY_PREFIX = "refresh:user:";
    private static final String USED_VALUE_PREFIX = "used:";

    private final StringRedisTemplate redisTemplate;

    // 토큰을 소유자와 함께 TTL을 걸어 저장하고, 사용자별 목록에도 추가한다
    public void save(String refreshToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(refreshToken), String.valueOf(userId), ttl);
        String userKey = userKey(userId);
        redisTemplate.opsForSet().add(userKey, refreshToken);
        redisTemplate.expire(userKey, ttl);
    }

    // 토큰으로 소유자와 소비 여부를 조회한다. TTL이 지났거나 삭제됐으면 빈 값을 반환한다
    public Optional<RefreshToken> findByToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(tokenKey(refreshToken));
        if (value == null) {
            return Optional.empty();
        }
        boolean used = value.startsWith(USED_VALUE_PREFIX);
        String userId = used ? value.substring(USED_VALUE_PREFIX.length()) : value;
        return Optional.of(new RefreshToken(refreshToken, Long.valueOf(userId), used));
    }

    // 토큰을 소비됨으로 표시한다. 값만 "used:"로 바꾸고 남은 TTL은 유지하며, TTL이 없으면 키를 지운다
    public void markUsed(RefreshToken refreshToken) {
        String tokenKey = tokenKey(refreshToken.token());
        Long remainingSeconds = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
        if (remainingSeconds == null || remainingSeconds <= 0) {
            redisTemplate.delete(tokenKey);
            return;
        }
        redisTemplate
                .opsForValue()
                .set(
                        tokenKey,
                        USED_VALUE_PREFIX + refreshToken.userId(),
                        Duration.ofSeconds(remainingSeconds));
    }

    // 토큰 하나를 지우고 사용자별 목록에서도 제거한다. 같은 사용자의 다른 토큰은 남는다
    public void delete(RefreshToken refreshToken) {
        redisTemplate.delete(tokenKey(refreshToken.token()));
        redisTemplate.opsForSet().remove(userKey(refreshToken.userId()), refreshToken.token());
    }

    // 해당 사용자에게 발급된 모든 토큰과 목록 자체를 지운다. 모든 기기가 로그아웃된다
    public void deleteAllByUserId(Long userId) {
        String userKey = userKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(this::tokenKey).toList());
        }
        redisTemplate.delete(userKey);
    }

    private String tokenKey(String refreshToken) {
        return TOKEN_KEY_PREFIX + refreshToken;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
