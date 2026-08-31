package com.toasty.domain.live.client.dto;

/** streamKey는 방송 권한 그 자체다. 로그·예외 메시지에 새지 않도록 toString에서 가린다. */
public record BroadcastCredential(String ingestEndpoint, String streamKey) {

    @Override
    public String toString() {
        return "BroadcastCredential[ingestEndpoint=" + ingestEndpoint + ", streamKey=***]";
    }
}
