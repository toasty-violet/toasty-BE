package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.client.dto.ChatToken;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 시청 화면이 IVS Chat에 붙을 때 쓴다. */
public record LiveChatTokenResponse(
        @Schema(description = "IVS Chat SDK에 넘길 토큰") String token,
        @Schema(description = "만료 시각. 이 전에 다시 발급받으세요") Instant expiresAt,
        @Schema(description = "메시지를 보낼 수 있는지. 비로그인은 읽기만 됩니다") boolean writable) {

    public static LiveChatTokenResponse of(ChatToken chatToken, boolean writable) {
        return new LiveChatTokenResponse(chatToken.token(), chatToken.expiresAt(), writable);
    }
}
