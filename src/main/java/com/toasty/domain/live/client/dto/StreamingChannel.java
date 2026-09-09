package com.toasty.domain.live.client.dto;

public record StreamingChannel(
        String channelArn, String playbackUrl, BroadcastCredential credential) {}
