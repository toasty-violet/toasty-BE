package com.toasty.domain.auth.entity;

// 리프레시 토큰 회전 시도의 결과. NOT_FOUND면 userId는 null이다.
public record RefreshTokenConsumeResult(Status status, Long userId) {

    public enum Status {
        // 이번 요청이 토큰을 소비했다. 이 상태는 동시 요청 중 정확히 하나에게만 돌아간다
        CONSUMED,
        // 이미 소비된 토큰이었다. 유출로 간주한다
        REUSED,
        // 만료됐거나 폐기된 토큰이다
        NOT_FOUND
    }

    public static RefreshTokenConsumeResult notFound() {
        return new RefreshTokenConsumeResult(Status.NOT_FOUND, null);
    }
}
