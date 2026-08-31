package com.toasty.global.auth;

/** 인증 도입 전까지의 임시 경계. JWT 인증이 붙으면 구현체만 교체한다. */
public interface SellerProvider {

    Long currentSellerId();
}
