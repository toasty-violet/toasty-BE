package com.toasty.domain.live.client.dto;

/** 송출 상태와 시청자 수. IVS가 한 번의 호출로 함께 주므로 나누지 않는다. */
public record StreamStatus(StreamState state, int viewerCount) {

    public static StreamStatus notBroadcasting() {
        return new StreamStatus(StreamState.NOT_BROADCASTING, 0);
    }

    public boolean isBroadcasting() {
        return state == StreamState.BROADCASTING;
    }
}
