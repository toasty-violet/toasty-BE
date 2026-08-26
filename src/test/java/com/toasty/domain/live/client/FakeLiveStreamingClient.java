package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamingChannel;
import java.util.ArrayList;
import java.util.List;

/** 실제 AWS를 부르지 않는 테스트 대역. 호출을 기록해 보상 삭제 검증에 쓴다. */
public class FakeLiveStreamingClient implements LiveStreamingClient {

    public static final String INGEST_ENDPOINT = "rtmps://ingest.example.com:443/app/";
    public static final String STREAM_KEY = "sk-test-key";

    private final List<String> createdChannelNames = new ArrayList<>();
    private final List<String> deletedChannelArns = new ArrayList<>();
    private final List<String> reissuedChannelArns = new ArrayList<>();
    private final List<String> streamKeyDeletedChannelArns = new ArrayList<>();
    private final List<String> stoppedChannelArns = new ArrayList<>();
    private RuntimeException deleteFailure;
    private RuntimeException reissueFailure;
    private StreamState streamState = StreamState.NOT_BROADCASTING;

    public static String arnOf(String channelName) {
        return "arn:aws:ivs:ap-northeast-2:123456789012:channel/" + channelName;
    }

    @Override
    public StreamingChannel createChannel(String channelName) {
        createdChannelNames.add(channelName);
        return new StreamingChannel(
                arnOf(channelName),
                "https://playback.example.com/" + channelName + ".m3u8",
                new BroadcastCredential(INGEST_ENDPOINT, STREAM_KEY));
    }

    @Override
    public void deleteChannel(String channelArn) {
        deletedChannelArns.add(channelArn);
        if (deleteFailure != null) {
            throw deleteFailure;
        }
    }

    @Override
    public BroadcastCredential reissueCredential(String channelArn) {
        if (reissueFailure != null) {
            throw reissueFailure;
        }
        reissuedChannelArns.add(channelArn);
        return new BroadcastCredential(
                INGEST_ENDPOINT, STREAM_KEY + "-" + reissuedChannelArns.size());
    }

    @Override
    public void deleteStreamKeys(String channelArn) {
        streamKeyDeletedChannelArns.add(channelArn);
    }

    @Override
    public StreamState getStreamState(String channelArn) {
        return streamState;
    }

    @Override
    public void stopStream(String channelArn) {
        stoppedChannelArns.add(channelArn);
    }

    public void failOnDelete(RuntimeException failure) {
        this.deleteFailure = failure;
    }

    public void failOnReissue(RuntimeException failure) {
        this.reissueFailure = failure;
    }

    public void broadcasting(StreamState streamState) {
        this.streamState = streamState;
    }

    public List<String> createdChannelNames() {
        return createdChannelNames;
    }

    public List<String> deletedChannelArns() {
        return deletedChannelArns;
    }

    public List<String> reissuedChannelArns() {
        return reissuedChannelArns;
    }

    public List<String> streamKeyDeletedChannelArns() {
        return streamKeyDeletedChannelArns;
    }

    public List<String> stoppedChannelArns() {
        return stoppedChannelArns;
    }
}
