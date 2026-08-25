package com.toasty.domain.auth.token;

import com.toasty.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// 액세스 토큰 생성/검증. 이 키로 서명하는 토큰은 액세스 토큰뿐이며,
// 리프레시 토큰은 JWT가 아니라 난수 문자열이라 RefreshTokenGenerator가 따로 만든다.
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

    // 토큰이 위조되지 않았고 아직 만료되지 않았는지 확인한 뒤, 토큰에 담긴 사용자 번호를 꺼내온다
    public Long parseAccessTokenUserId(String token) {
        Claims claims =
                Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
