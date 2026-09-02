package com.toasty.domain.auth.filter;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.auth.exception.AuthErrorCode;
import com.toasty.domain.auth.token.JwtTokenProvider;
import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 클라이언트가 보내는 액세스 토큰을 검증해 요청의 주체를 확정한다.
// 인증이 필요한 API는 {@code @SellerOnly} 같은 인가 annotation을 활용한다.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserService userService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticate(token));
            SecurityContextHolder.setContext(context);
        } catch (CustomException e) {
            SecurityContextHolder.clearContext();
            // 필터에서 던진 예외는 DispatcherServlet 밖이라 @RestControllerAdvice까지 올라가지 않는다.
            // 리졸버에 넘겨야 GlobalExceptionHandler가 만드는 ApiResponse 형식으로 응답이 나간다.
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // 토큰은 유효해도 그 사이 탈퇴했을 수 있어, 매 요청마다 역할을 조회해 role을 갱신한다.
    private Authentication authenticate(String token) {
        Long userId = jwtTokenProvider.parseAccessTokenUserId(token);
        AuthUser authUser =
                userService
                        .findAuthUser(userId)
                        .orElseThrow(() -> new CustomException(AuthErrorCode.ACCESS_TOKEN_INVALID));
        return new UsernamePasswordAuthenticationToken(
                authUser, null, toAuthorities(authUser.role()));
    }

    // role이 없으면 권한 없이 인증만 시킨다. 역할을 요구하는 API에서는 접근이 거부된다
    private Collection<? extends GrantedAuthority> toAuthorities(Role role) {
        if (role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
