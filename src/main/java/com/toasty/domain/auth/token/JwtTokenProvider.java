package com.toasty.domain.auth.token;

import com.toasty.domain.auth.exception.AuthErrorCode;
import com.toasty.global.config.JwtProperties;
import com.toasty.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// 액세스 토큰 생성/검증
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.secretKey =
                Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = jwtProperties.accessTokenExpiration();
    }

    // 액세스 토큰 발급 — userId를 subject로 담아 서명한다
    public String generateAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    // 토큰의 위조여 및 만료 여부를 확인한 뒤, 토큰에 담긴 userId를 꺼내온다.
    public Long parseAccessTokenUserId(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(secretKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new CustomException(AuthErrorCode.ACCESS_TOKEN_EXPIRED, e);
        } catch (JwtException | IllegalArgumentException e) {
            // 토큰이 비었거나 subject가 숫자가 아닐 때 나온다.
            throw new CustomException(AuthErrorCode.ACCESS_TOKEN_INVALID, e);
        }
    }
}
