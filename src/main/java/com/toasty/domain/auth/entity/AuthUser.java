package com.toasty.domain.auth.entity;

import com.toasty.domain.user.entity.Role;

// 액세스 토큰에서 추출한 userId로 조회해 유저 정보와 역할, 역할에 해당하는 프로필 번호를 반환한다.
// 역할이 배타적이라 customerId와 sellerId 중 하나만 값을 가지며, 온보딩 전이면 둘 다 null이다.
public record AuthUser(Long userId, Role role, Long customerId, Long sellerId) {

    public boolean hasProfile() {
        return customerId != null || sellerId != null;
    }
}
