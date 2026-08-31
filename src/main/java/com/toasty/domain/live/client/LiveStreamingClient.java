package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.StreamingChannel;

/** 방송 송출 인프라 경계. Service가 AWS SDK를 직접 알지 않도록 분리한다. */
public interface LiveStreamingClient {

    StreamingChannel createChannel(String channelName);

    void deleteChannel(String channelArn);
}
