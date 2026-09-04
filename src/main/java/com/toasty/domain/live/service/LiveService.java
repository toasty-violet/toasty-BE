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
import com.toasty.domain.live.entity.LiveUpdateCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.service.ProductService;
import com.toasty.global.exception.CustomException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveService {

    private final LiveRepository liveRepository;
    private final LiveStreamingClient liveStreamingClient;
    private final ProductService productService;
    private final TransactionTemplate transactionTemplate;

    /** 셀러가 라이브를 개설하면서 이번 방송에서 팔 상품을 함께 등록한다. */
    // AWS 호출은 수 초가 걸려 DB 커넥션을 잡고 있으면 안 되므로 채널 생성도 사진 복사도 트랜잭션 밖에 둔다.
    // 사진 복사를 채널 생성보다 앞에 두어, 사진이 없는 요청은 채널을 만들기 전에 걸러낸다.
    // 대신 저장은 테이블 4개에 걸치므로 TransactionTemplate으로 묶어, 중간에 실패하면
    // 상품 없는 빈 라이브가 남지 않게 한다. 실패하면 이미 만든 IVS 채널과 복사한 사진을 지운다.
    public LiveCreateResponse create(LiveCreateCommand command) {
        List<String> imageObjectKeys =
                productService.copyImagesToPermanent(command.sellerId(), command.products());

        StreamingChannel channel = null;
        try {
            channel = liveStreamingClient.createChannel(generateChannelName(command.sellerId()));
            StreamingChannel created = channel;
            return transactionTemplate.execute(
                    status -> {
                        Live live =
                                liveRepository.save(
                                        Live.create(
                                                command,
                                                created.channelArn(),
                                                created.playbackUrl()));
                        List<LiveProductResponse> products =
                                productService.registerForLive(
                                        live.getId(),
                                        command.sellerId(),
                                        command.products(),
                                        imageObjectKeys);
                        return LiveCreateResponse.of(live, products);
                    });
        } catch (RuntimeException e) {
            if (channel != null) {
                deleteChannelQuietly(channel.channelArn());
            }
            productService.deleteImagesQuietly(imageObjectKeys);
            throw e;
        }
    }

    /** 셀러가 방송 전에 라이브 내용과 편성 상품을 고친다. */
    // create와 같은 이유로 사진 복사를 트랜잭션 앞에 둔다. 수정할 수 없는 라이브는 복사 전에 걸러낸다.
    // 편성에서 빠진 사진은 커밋된 뒤에 지운다. 먼저 지우면 트랜잭션이 깨졌을 때 사진이 사라진다.
    public LiveDetailResponse update(LiveUpdateCommand command) {
        requireEditableOwnLive(findById(command.liveId()), command.sellerId());

        List<String> copiedImageKeys =
                command.products() == null
                        ? List.of()
                        : productService.copyNewImagesToPermanent(
                                command.sellerId(), command.products());

        List<String> obsoleteImageKeys = new ArrayList<>();
        LiveDetailResponse response;
        try {
            response =
                    transactionTemplate.execute(
                            status -> {
                                Live live = findById(command.liveId());
                                live.update(
                                        command.title(),
                                        command.description(),
                                        command.scheduledAt());
                                if (command.products() != null) {
                                    obsoleteImageKeys.addAll(
                                            productService.replaceForLive(
                                                    command.liveId(),
                                                    command.sellerId(),
                                                    command.products(),
                                                    copiedImageKeys));
                                }
                                return LiveDetailResponse.from(live);
                            });
        } catch (RuntimeException e) {
            productService.deleteImagesQuietly(copiedImageKeys);
            throw e;
        }

        // 여기서 실패해도 되돌리지 않는다. 이미 커밋돼서 새 사진은 DB가 참조하고 있다.
        productService.deleteImagesQuietly(obsoleteImageKeys);
        return response;
    }

    @Transactional(readOnly = true)
    public LiveDetailResponse getByPublicId(String publicId) {
        return LiveDetailResponse.from(findByPublicId(publicId));
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
    public LivePlaybackResponse getPlayback(String publicId) {
        return LivePlaybackResponse.from(findByPublicId(publicId));
    }

    public LiveDetailResponse end(Long liveId, Long sellerId) {
        Live live = findById(liveId);
        if (!live.isOwnedBy(sellerId)) {
            throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
        }
        if (live.isEnded()) {
            return LiveDetailResponse.from(live);
        }
        liveStreamingClient.stopStream(live.getIvsChannelArn());
        liveStreamingClient.deleteStreamKeys(live.getIvsChannelArn());
        live.end();
        return LiveDetailResponse.from(liveRepository.save(live));
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

    private void requireEditableOwnLive(Live live, Long sellerId) {
        if (!live.isOwnedBy(sellerId)) {
            throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
        }
        if (!live.isEditable()) {
            throw new CustomException(LiveErrorCode.LIVE_NOT_EDITABLE);
        }
    }

    private Live findById(Long liveId) {
        return liveRepository
                .findById(liveId)
                .orElseThrow(() -> new CustomException(LiveErrorCode.LIVE_NOT_FOUND));
    }

    private Live findByPublicId(String publicId) {
        return liveRepository
                .findByPublicId(publicId)
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
