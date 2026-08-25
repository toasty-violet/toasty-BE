package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.config.IvsProperties;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.InternalServerException;
import software.amazon.awssdk.services.ivs.model.ServiceUnavailableException;
import software.amazon.awssdk.services.ivs.model.ThrottlingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsIvsStreamingClient implements LiveStreamingClient {

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
        } catch (ThrottlingException
                | ServiceUnavailableException
                | InternalServerException
                | SdkClientException e) {
            log.warn("IVS 채널 생성 일시 실패 - channelName={}", channelName, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
        } catch (Exception e) {
            // 권한 부족·설정 오류·채널 한도 초과 등. 사람이 손대야 하므로 error로 남긴다.
            log.error("IVS 채널 생성 실패 - channelName={}", channelName, e);
            throw new CustomException(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED, e);
        }
    }

    @Override
    public void deleteChannel(String channelArn) {
        try {
            ivsClient.deleteChannel(request -> request.arn(channelArn));
        } catch (ThrottlingException
                | ServiceUnavailableException
                | InternalServerException
                | SdkClientException e) {
            log.warn("IVS 채널 삭제 일시 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("IVS 채널 삭제 실패 - channelArn={}", channelArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CHANNEL_DELETE_FAILED, e);
        }
    }
}
