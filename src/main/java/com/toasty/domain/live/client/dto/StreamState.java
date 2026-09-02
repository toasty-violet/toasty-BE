package com.toasty.domain.live.client.dto;

/** 미송출은 장애가 아니라 정상 상태이므로 예외가 아닌 값으로 다룬다. */
public enum StreamState {
    BROADCASTING,
    NOT_BROADCASTING
}
