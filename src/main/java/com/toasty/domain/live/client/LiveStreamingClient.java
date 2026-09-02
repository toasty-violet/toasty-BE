package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamingChannel;

/** 방송 송출 인프라 경계. Service가 AWS SDK를 직접 알지 않도록 분리한다. */
public interface LiveStreamingClient {

    StreamingChannel createChannel(String channelName);

    void deleteChannel(String channelArn);

    BroadcastCredential reissueCredential(String channelArn);

    void deleteStreamKeys(String channelArn);

    StreamState getStreamState(String channelArn);

    /** 송출 중이 아니어도 예외를 던지지 않는다. */
    void stopStream(String channelArn);
}
