package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.config.IvsProperties;
import com.toasty.global.exception.CustomException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.RetryableException;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.CreateStreamKeyResponse;
import software.amazon.awssdk.services.ivs.model.GetChannelResponse;
import software.amazon.awssdk.services.ivs.model.InternalServerException;
import software.amazon.awssdk.services.ivs.model.ListStreamKeysResponse;
import software.amazon.awssdk.services.ivs.model.ServiceUnavailableException;
import software.amazon.awssdk.services.ivs.model.StreamKeySummary;
import software.amazon.awssdk.services.ivs.model.ThrottlingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsIvsStreamingClient implements LiveStreamingClient {

    private static final int MAX_CAUSE_DEPTH = 10;

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
                                            .latencyMode(ivsProperties.latencyMode()));
            return new StreamingChannel(
                    response.channel().arn(),
                    response.channel().playbackUrl(),
                    new BroadcastCredential(
                            response.channel().ingestEndpoint(), response.streamKey().value()));
        } catch (Exception e) {
            if (isTransient(e)) {
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
            if (isTransient(e)) {
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
        } catch (Exception e) {
            if (isTransient(e)) {
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
            if (isTransient(e)) {
                log.warn("송출 키 삭제 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("송출 키 삭제 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAM_KEY_DELETE_FAILED, e);
        }
    }

    @Override
    public StreamState getStreamState(String channelArn) {
        try {
            ivsClient.getStream(request -> request.channelArn(channelArn));
            return StreamState.BROADCASTING;
        } catch (ChannelNotBroadcastingException e) {
            return StreamState.NOT_BROADCASTING;
        } catch (Exception e) {
            if (isTransient(e)) {
                log.warn("송출 상태 조회 일시 실패 - channelArn={}", channelArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("송출 상태 조회 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAM_STATUS_FETCH_FAILED, e);
        }
    }

    @Override
    public void stopStream(String channelArn) {
        try {
            ivsClient.stopStream(request -> request.channelArn(channelArn));
        } catch (ChannelNotBroadcastingException e) {
            log.debug("송출 중이 아니라 중단을 건너뛴다 - channelArn={}", channelArn);
        } catch (Exception e) {
            if (isTransient(e)) {
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
            ivsClient.deleteStreamKey(request -> request.arn(key.arn()));
        }
    }

    /**
     * 잠시 후 재시도하면 성공할 수 있는 실패인지 판정한다. SdkException.retryable()은 RetryableException 외에는 전부 false를 반환해
     * 신뢰할 수 없으므로 타입과 원인 사슬로 판정한다.
     */
    private static boolean isTransient(Throwable e) {
        if (e instanceof ThrottlingException
                || e instanceof ServiceUnavailableException
                || e instanceof InternalServerException
                || e instanceof ApiCallTimeoutException
                || e instanceof ApiCallAttemptTimeoutException
                || e instanceof RetryableException) {
            return true;
        }
        // 네트워크 오류와 자격증명 실패가 둘 다 SdkClientException으로 오므로 타입으로 구분되지 않는다.
        // 네트워크 쪽만 원인 사슬에 IOException을 달고 온다.
        Throwable cause = e.getCause();
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
