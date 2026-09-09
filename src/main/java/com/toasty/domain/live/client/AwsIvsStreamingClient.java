package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamStatus;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.config.IvsProperties;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.CreateStreamKeyResponse;
import software.amazon.awssdk.services.ivs.model.GetChannelResponse;
import software.amazon.awssdk.services.ivs.model.InternalServerException;
import software.amazon.awssdk.services.ivs.model.ListStreamKeysResponse;
import software.amazon.awssdk.services.ivs.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivs.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.ivs.model.ServiceUnavailableException;
import software.amazon.awssdk.services.ivs.model.Stream;
import software.amazon.awssdk.services.ivs.model.StreamKeySummary;
import software.amazon.awssdk.services.ivs.model.ThrottlingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsIvsStreamingClient implements LiveStreamingClient {

    // AWS 기본값과 같지만, 기본값이 바뀌어도 흔들리지 않도록 명시한다.
    private static final String LATENCY_MODE = "LOW";

    private final IvsClient ivsClient;
    private final IvsProperties ivsProperties;

    @Override
    public StreamingChannel createChannel(String channelName) {
        try {
            // CreateChannel 응답에 스트림 키가 함께 오므로 CreateStreamKey를 따로 부르지 않는다.
            CreateChannelResponse response =
                    ivsClient.createChannel(
                            request ->
                                    request.name(channelName)
                                            .type(ivsProperties.channelType())
                                            .latencyMode(LATENCY_MODE));
            return new StreamingChannel(
                    response.channel().arn(),
                    response.channel().playbackUrl(),
                    new BroadcastCredential(
                            response.channel().ingestEndpoint(), response.streamKey().value()));
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("IVS 채널 생성 일시 실패 - channelName={}", channelName, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("IVS 채널 생성 실패 - channelName={}", channelName, e);
            throw new CustomException(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED, e);
        }
    }

    @Override
    public void deleteChannel(String channelArn) {
        try {
            ivsClient.deleteChannel(request -> request.arn(channelArn));
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("IVS 채널 삭제 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("IVS 채널 삭제 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CHANNEL_DELETE_FAILED, e);
        }
    }

    @Override
    public BroadcastCredential reissueCredential(String channelArn) {
        try {
            // 채널당 스트림 키는 1개이고 덮어쓸 수 없다. 기존 키를 지운 뒤에야 새로 만들 수 있다.
            deleteAllStreamKeys(channelArn);
            CreateStreamKeyResponse created =
                    ivsClient.createStreamKey(request -> request.channelArn(channelArn));
            GetChannelResponse channel = ivsClient.getChannel(request -> request.arn(channelArn));
            return new BroadcastCredential(
                    channel.channel().ingestEndpoint(), created.streamKey().value());
        } catch (ServiceQuotaExceededException e) {
            log.warn("송출정보 재발급 경쟁 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CREDENTIAL_REISSUE_CONFLICT, e);
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("송출정보 재발급 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("송출정보 재발급 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CREDENTIAL_REISSUE_FAILED, e);
        }
    }

    @Override
    public void deleteStreamKeys(String channelArn) {
        try {
            deleteAllStreamKeys(channelArn);
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("송출 키 삭제 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("송출 키 삭제 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAM_KEY_DELETE_FAILED, e);
        }
    }

    @Override
    public StreamStatus getStreamStatus(String channelArn) {
        try {
            var stream = ivsClient.getStream(request -> request.channelArn(channelArn)).stream();
            return new StreamStatus(StreamState.BROADCASTING, toViewerCount(stream));
        } catch (ChannelNotBroadcastingException e) {
            return StreamStatus.notBroadcasting();
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("송출 상태 조회 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("송출 상태 조회 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAM_STATUS_FETCH_FAILED, e);
        }
    }

    // 송출 중이라는 사실이 먼저다. 시청자 수가 비어 오더라도 상태 조회를 실패시키지 않는다.
    private int toViewerCount(Stream stream) {
        if (stream == null || stream.viewerCount() == null) {
            return 0;
        }
        return Math.toIntExact(stream.viewerCount());
    }

    @Override
    public void stopStream(String channelArn) {
        try {
            ivsClient.stopStream(request -> request.channelArn(channelArn));
        } catch (ChannelNotBroadcastingException e) {
            log.debug("송출 중이 아니라 중단을 건너뛴다 - channelArn={}", channelArn);
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e,
                    ThrottlingException.class,
                    ServiceUnavailableException.class,
                    InternalServerException.class)) {
                log.warn("방송 중단 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("방송 중단 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_BROADCAST_STOP_FAILED, e);
        }
    }

    private void deleteAllStreamKeys(String channelArn) {
        ListStreamKeysResponse keys =
                ivsClient.listStreamKeys(request -> request.channelArn(channelArn));
        for (StreamKeySummary key : keys.streamKeys()) {
            try {
                ivsClient.deleteStreamKey(request -> request.arn(key.arn()));
            } catch (ResourceNotFoundException alreadyDeleted) {
            }
        }
    }
}
