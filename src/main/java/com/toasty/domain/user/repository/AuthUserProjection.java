package com.toasty.domain.user.repository;

// 인증에 필요한 유저 정보와 역할별 프로필 번호를 한 번에 담는다.
public interface AuthUserProjection {

    Long getUserId();

    // 온보딩 전까지 null이라 enum이 아닌 문자열로 받는다
    String getRole();

    Long getCustomerId();

    Long getSellerId();
}
