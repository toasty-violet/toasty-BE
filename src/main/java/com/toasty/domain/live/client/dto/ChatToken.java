package com.toasty.domain.live.client.dto;

import java.time.Instant;

/** 채팅방 입장 토큰과 만료 시각. */
public record ChatToken(String token, Instant expiresAt) {}
