package com.toasty.domain.user.entity;

import com.toasty.domain.auth.entity.AuthUser;

/** 탈퇴 요청은 입력값이 없어, 정리할 대상을 인증 정보에서 그대로 가져온다. */
public record UserWithdrawCommand(Long userId, Long customerId, Long sellerId) {

    public static UserWithdrawCommand from(AuthUser user) {
        return new UserWithdrawCommand(user.userId(), user.customerId(), user.sellerId());
    }
}
