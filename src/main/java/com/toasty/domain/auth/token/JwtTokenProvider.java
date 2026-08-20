package com.toasty.domain.auth.token;

import com.toasty.global.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
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
