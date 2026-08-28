package com.toasty.domain.auth.repository;

import com.toasty.domain.auth.entity.RefreshToken;
import com.toasty.domain.auth.entity.RefreshTokenConsumeResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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

    // GET과 SET을 한 번의 원자적 실행으로 묶는다. 나눠 쓰면 동시 재발급 요청이 둘 다 미소비 상태를
    // 읽어 통과하거나(재사용 미탐지), 한쪽이 상대의 표시 직후에 도착해 유출로 오인된다.
    // KEEPTTL로 남은 TTL을 그대로 두어 만료 시각이 뒤로 밀리지 않게 한다 (Redis 6.0+).
    private static final RedisScript<String> CONSUME_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return nil
                    end
                    local prefix = ARGV[1]
                    if string.sub(value, 1, #prefix) == prefix then
                        return 'reused:' .. string.sub(value, #prefix + 1)
                    end
                    redis.call('SET', KEYS[1], prefix .. value, 'KEEPTTL')
                    return 'consumed:' .. value
                    """,
                    String.class);

    private final StringRedisTemplate redisTemplate;

    // 토큰을 소유자와 함께 TTL을 걸어 저장하고, 사용자별 목록에도 추가한다
    public void save(String refreshToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(refreshToken), String.valueOf(userId), ttl);
        String userKey = userKey(userId);
        redisTemplate.opsForSet().add(userKey, refreshToken);
        redisTemplate.expire(userKey, ttl);
    }

    // 토큰으로 소유자를 조회한다. TTL이 지났거나 삭제됐으면 빈 값을 반환한다
    public Optional<RefreshToken> findByToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(tokenKey(refreshToken));
        if (value == null) {
            return Optional.empty();
        }
        String userId =
                value.startsWith(USED_VALUE_PREFIX)
                        ? value.substring(USED_VALUE_PREFIX.length())
                        : value;
        return Optional.of(new RefreshToken(refreshToken, Long.valueOf(userId)));
    }

    // 토큰을 소비됨으로 표시하면서 그 결과를 함께 돌려준다. 조회와 표시가 한 번에 일어나므로
    // 같은 토큰으로 동시에 여러 요청이 와도 CONSUMED는 하나만 나오고 나머지는 REUSED가 된다
    public RefreshTokenConsumeResult consume(String refreshToken) {
        String result =
                redisTemplate.execute(
                        CONSUME_SCRIPT, List.of(tokenKey(refreshToken)), USED_VALUE_PREFIX);
        if (result == null) {
            return RefreshTokenConsumeResult.notFound();
        }
        int separator = result.indexOf(':');
        Long userId = Long.valueOf(result.substring(separator + 1));
        return result.startsWith("consumed:")
                ? new RefreshTokenConsumeResult(RefreshTokenConsumeResult.Status.CONSUMED, userId)
                : new RefreshTokenConsumeResult(RefreshTokenConsumeResult.Status.REUSED, userId);
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
