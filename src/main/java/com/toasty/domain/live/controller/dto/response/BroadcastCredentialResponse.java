package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import io.swagger.v3.oas.annotations.media.Schema;

/** 응답으로 단 한 번만 전달되는 민감정보. 저장하지 않으며 toString에서도 가린다. */
public record BroadcastCredentialResponse(
        @Schema(description = "IVS Web Broadcast SDK에 넘길 송출 엔드포인트") String ingestEndpoint,
        @Schema(description = "송출 권한을 가진 비밀값. 다시 조회할 수 없고 재발급만 가능하다.") String streamKey) {

    public static BroadcastCredentialResponse from(BroadcastCredential credential) {
        return new BroadcastCredentialResponse(credential.ingestEndpoint(), credential.streamKey());
    }

    @Override
    public String toString() {
        return "BroadcastCredentialResponse[ingestEndpoint=" + ingestEndpoint + ", streamKey=***]";
    }
}
