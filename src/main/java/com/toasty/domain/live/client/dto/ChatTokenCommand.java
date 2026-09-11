package com.toasty.domain.live.client.dto;

/** 채팅방 입장 토큰 발급 요청. */
// writable이 거짓이면 읽기 전용으로 발급해 비로그인 시청자는 메시지를 보낼 수 없다.
public record ChatTokenCommand(
        String chatRoomArn,
        String chatUserId,
        String displayName,
        ChatRole role,
        boolean writable) {}
