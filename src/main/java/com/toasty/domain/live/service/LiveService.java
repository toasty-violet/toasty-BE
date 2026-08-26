package com.toasty.domain.live.service;

import com.toasty.domain.live.client.LiveStreamingClient;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveCreateResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.global.exception.CustomException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveService {

    private final LiveRepository liveRepository;
    private final LiveStreamingClient liveStreamingClient;

    /**
     * 외부 AWS 호출이 포함되므로 메서드 전체를 트랜잭션으로 묶지 않는다. 저장은 {@code liveRepository.save()} 자체 트랜잭션으로 충분하고, 저장이
     * 실패하면 이미 만들어진 IVS 채널을 지워 고아 리소스를 남기지 않는다.
     */
    public LiveCreateResponse create(LiveCreateCommand command) {
        StreamingChannel channel =
                liveStreamingClient.createChannel(generateChannelName(command.sellerId()));
        try {
            Live live =
                    liveRepository.save(
                            Live.create(command, channel.channelArn(), channel.playbackUrl()));
            return LiveCreateResponse.of(live, channel.credential());
        } catch (RuntimeException e) {
            deleteChannelQuietly(channel.channelArn());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public LiveDetailResponse getById(Long liveId) {
        return LiveDetailResponse.from(findById(liveId));
    }

    public BroadcastCredentialResponse reissueCredential(Long liveId, Long sellerId) {
        Live live = findById(liveId);
        if (!live.isOwnedBy(sellerId)) {
            throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
        }
        if (live.isEnded()) {
            throw new CustomException(LiveErrorCode.LIVE_ALREADY_ENDED);
        }
        return BroadcastCredentialResponse.from(
                liveStreamingClient.reissueCredential(live.getIvsChannelArn()));
    }

    public LiveStreamStatusResponse getStreamStatus(Long liveId, Long sellerId) {
        Live live = findById(liveId);
        if (!live.isOwnedBy(sellerId)) {
            throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
        }
        StreamState streamState = liveStreamingClient.getStreamState(live.getIvsChannelArn());
        if (streamState == StreamState.BROADCASTING && !live.isEnded()) {
            live = syncToBroadcasting(live);
        }
        return LiveStreamStatusResponse.of(live, streamState);
    }

    @Transactional(readOnly = true)
    public LivePlaybackResponse getPlayback(Long liveId) {
        return LivePlaybackResponse.from(findById(liveId));
    }

    private Live syncToBroadcasting(Live live) {
        if (live.isBroadcasting()) {
            return live;
        }
        live.startBroadcast();
        try {
            return liveRepository.save(live);
        } catch (DataIntegrityViolationException e) {
            // active_seller_id unique 위반. 이 셀러가 다른 라이브를 이미 방송 중이다.
            throw new CustomException(LiveErrorCode.LIVE_ALREADY_BROADCASTING, e);
        }
    }

    private Live findById(Long liveId) {
        return liveRepository
                .findById(liveId)
                .orElseThrow(() -> new CustomException(LiveErrorCode.LIVE_NOT_FOUND));
    }

    // IVS 채널명은 [a-zA-Z0-9-_]만 허용하고 128자를 넘을 수 없다.
    private String generateChannelName(Long sellerId) {
        return "toasty-live-" + sellerId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // 보상 삭제가 실패해도 원래 예외를 가리지 않는다. 고아 채널은 로그로 추적한다.
    private void deleteChannelQuietly(String channelArn) {
        try {
            liveStreamingClient.deleteChannel(channelArn);
        } catch (RuntimeException e) {
            log.error("보상 삭제 실패. IVS에 고아 채널이 남았다 - channelArn={}", channelArn, e);
        }
    }
}
