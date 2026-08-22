package com.toasty.domain.auth.token;

import com.toasty.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// 액세스/리프레시 토큰 생성
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.secretKey =
                Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = jwtProperties.accessTokenExpirationMillis();
        this.refreshTokenExpirationMillis = jwtProperties.refreshTokenExpirationMillis();
    }

    // 액세스 토큰 발급
    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessTokenExpirationMillis);
    }

    // 리프레시 토큰 발급
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, refreshTokenExpirationMillis);
    }

    // 리프레시 토큰 TTL — Redis 저장, 쿠키 만료시간에 사용
    public Duration getRefreshTokenExpiration() {
        return Duration.ofMillis(refreshTokenExpirationMillis);
    }

    // 토큰이 위조되지 않았고 아직 만료되지 않았는지 확인한 뒤, 토큰에 담긴 사용자 번호를 꺼내온다
    public Long parseUserId(String token) {
        Claims claims =
                Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    // 로그아웃처럼 "이미 만료된 토큰이어도 일단 사용자만 알면 되는" 상황을 위한 것 — 위조된 토큰이면 포기하고 빈 값을 준다
    public Optional<Long> parseUserIdIgnoringExpiration(String token) {
        try {
            return Optional.of(parseUserId(token));
        } catch (ExpiredJwtException e) {
            return Optional.ofNullable(e.getClaims().getSubject()).map(Long::valueOf);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // userId를 subject로 담아 서명된 JWT 생성
    private String generateToken(Long userId, long expirationMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }
}
