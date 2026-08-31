package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
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
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.InternalServerException;
import software.amazon.awssdk.services.ivs.model.ServiceUnavailableException;
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
