package com.toasty.domain.live.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.live.client.LiveChatClient;
import com.toasty.domain.live.client.LiveStreamingClient;
import com.toasty.domain.live.client.dto.ChatRole;
import com.toasty.domain.live.client.dto.ChatTokenCommand;
import com.toasty.domain.live.client.dto.StreamStatus;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveChatTokenResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.controller.dto.response.LiveViewerCountResponse;
import com.toasty.domain.live.controller.dto.response.LiveViewerResponse;
import com.toasty.domain.live.controller.dto.response.LiveWithProductsResponse;
import com.toasty.domain.live.controller.dto.response.SellerLiveTabResponse;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.live.entity.LiveUpdateCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.domain.live.repository.LiveViewerCountRepository;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.entity.LiveProductPinCommand;
import com.toasty.domain.product.entity.LiveProductUpdateCommand;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // 방송이 끝나도 결제 시트에 남아 있는 시청자가 있어, 이만큼 지난 뒤에 채팅방을 회수한다.
    private static final Duration CHAT_ROOM_RETENTION = Duration.ofMinutes(30);

    private final LiveRepository liveRepository;
    private final LiveViewerCountRepository liveViewerCountRepository;
    private final LiveStreamingClient liveStreamingClient;
    private final LiveChatClient liveChatClient;
    private final ProductService productService;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    /** 셀러가 라이브를 개설하면서 이번 방송에서 팔 상품을 함께 등록한다. */
    // AWS 호출은 수 초가 걸려 DB 커넥션을 잡고 있으면 안 되므로 채널 생성도 사진 복사도 트랜잭션 밖에 둔다.
    // 사진 복사를 채널 생성보다 앞에 두어, 사진이 없는 요청은 채널을 만들기 전에 걸러낸다.
    // 대신 저장은 테이블 4개에 걸치므로 TransactionTemplate으로 묶어, 중간에 실패하면
    // 상품 없는 빈 라이브가 남지 않게 한다. 실패하면 이미 만든 IVS 채널과 복사한 사진을 지운다.
    public LiveWithProductsResponse create(LiveCreateCommand command) {
        requireScheduleRoom(command.sellerId());

        List<String> imageObjectKeys =
                productService.copyImagesToPermanent(command.sellerId(), command.products());

        StreamingChannel channel = null;
        String chatRoomArn = null;
        try {
            // 채널과 채팅방에 같은 이름을 준다. 콘솔에서 어느 라이브의 것인지 짝지어 보기 위해서다.
            String resourceName = generateResourceName(command.sellerId());
            channel = liveStreamingClient.createChannel(resourceName);
            chatRoomArn = liveChatClient.createRoom(resourceName);
            StreamingChannel created = channel;
            String createdChatRoomArn = chatRoomArn;
            return transactionTemplate.execute(
                    status -> {
                        Live live =
                                liveRepository.save(
                                        Live.create(
                                                command,
                                                created.channelArn(),
                                                created.playbackUrl(),
                                                createdChatRoomArn));
                        List<LiveProductResponse> products =
                                productService.registerForLive(
                                        live.getId(),
                                        command.sellerId(),
                                        command.products(),
                                        imageObjectKeys);
                        return LiveWithProductsResponse.of(live, products);
                    });
        } catch (RuntimeException e) {
            deleteChatRoomQuietly(chatRoomArn);
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
                                // 사진 복사가 도는 동안 송출이 시작됐을 수 있어 트랜잭션 안에서 다시 본다.
                                requireEditableOwnLive(live, command.sellerId());
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

    /** 셀러가 방송 전에 저장해둔 라이브를 지운다. 편성 상품과 사진, IVS 채널도 함께 정리한다. */
    // 검사와 삭제를 한 트랜잭션에 두지만, status는 셀러의 송출 상태 폴링으로만 갱신돼서
    // 폴링 전이면 실제로 송출 중이어도 READY로 보여 지워진다.
    // IVS 채널과 S3 객체는 커밋된 뒤에 지운다. 먼저 지우면 트랜잭션이 깨졌을 때 되살릴 수 없다.
    public void delete(Long liveId, Long sellerId) {
        List<String> obsoleteImageKeys = new ArrayList<>();
        DeletedResources resources =
                transactionTemplate.execute(
                        status -> {
                            Live live = findById(liveId);
                            if (!live.isOwnedBy(sellerId)) {
                                throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
                            }
                            if (!live.isDeletable()) {
                                throw new CustomException(LiveErrorCode.LIVE_NOT_DELETABLE);
                            }
                            obsoleteImageKeys.addAll(productService.removeAllForLive(liveId));
                            liveRepository.delete(live);
                            return new DeletedResources(
                                    live.getIvsChannelArn(), live.getIvsChatRoomArn());
                        });

        // 여기서 실패해도 되돌리지 않는다. 라이브는 이미 지워졌고 남은 자원은 로그로 추적한다.
        deleteChatRoomQuietly(resources.chatRoomArn());
        deleteChannelQuietly(resources.channelArn());
        productService.deleteImagesQuietly(obsoleteImageKeys);
    }

    /** 셀러가 탈퇴할 때 아직 끝나지 않은 라이브를 정리한다. 방송 중이면 끝내고, 예정 라이브는 지운다. */
    // 지난 방송은 시청자와 주문 이력이 참조하므로 건드리지 않는다.
    public void cleanUpForSellerWithdrawal(Long sellerId) {
        List<Live> lives =
                liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                        sellerId, List.of(LiveStatus.LIVE, LiveStatus.READY));
        for (Live live : lives) {
            if (live.isBroadcasting()) {
                end(live.getId(), sellerId);
            } else {
                delete(live.getId(), sellerId);
            }
        }
    }

    // 커밋된 뒤에 지울 외부 자원. 라이브 행이 사라진 뒤에도 ARN을 들고 있어야 한다.
    private record DeletedResources(String channelArn, String chatRoomArn) {}

    /** 셀러가 라이브 하나를 편성 상품까지 가져온다. 수정 화면을 채우는 데 쓴다. */
    // 상태로 막지 않는다. 방송 중에도 편성 상품을 읽어야 하고, 고칠 수 있는지는 update가 판단한다.
    @Transactional(readOnly = true)
    public LiveWithProductsResponse getMyLiveDetail(Long liveId, Long sellerId) {
        Live live = requireOwnLive(liveId, sellerId);
        return LiveWithProductsResponse.of(live, productService.findScheduledProducts(liveId));
    }

    /** 셀러가 방송 화면에서 전체 상품 시트를 연다. */
    @Transactional(readOnly = true)
    public LiveProductsResponse getMyLiveProducts(Long liveId, Long sellerId) {
        requireOwnLive(liveId, sellerId);
        return productService.findLiveProducts(liveId);
    }

    /** 셀러가 방송 중에 소개할 상품을 고정한다. */
    @Transactional
    public void pinProduct(LiveProductPinCommand command) {
        requireBroadcastingOwnLive(command.liveId(), command.sellerId());
        productService.pinForLive(command);
    }

    /** 셀러가 방송 중에 상품의 가격과 재고를 고친다. */
    @Transactional
    public void changeProductPriceAndStock(LiveProductUpdateCommand command) {
        requireBroadcastingOwnLive(command.liveId(), command.sellerId());
        productService.changePriceAndStockDuringLive(command);
    }

    private Live requireOwnLive(Long liveId, Long sellerId) {
        Live live = findById(liveId);
        if (!live.isOwnedBy(sellerId)) {
            throw new CustomException(LiveErrorCode.LIVE_FORBIDDEN);
        }
        return live;
    }

    // lives.status는 스트림 상태 조회가 LIVE로 옮긴다. 방송 화면이 그 조회를 하고 있어야 고정과 수정이 열린다.
    private void requireBroadcastingOwnLive(Long liveId, Long sellerId) {
        if (!requireOwnLive(liveId, sellerId).isBroadcasting()) {
            throw new CustomException(LiveErrorCode.LIVE_NOT_BROADCASTING);
        }
    }

    /** 셀러가 라이브탭에서 자기 라이브 상황을 한 번에 본다. */
    // 라이브 한 번, 편성 상품 수 한 번으로 끝낸다. 라이브마다 상품을 세면 개수만큼 쿼리가 늘어난다.
    @Transactional(readOnly = true)
    public SellerLiveTabResponse getMyLiveTab(Long sellerId) {
        List<Live> lives =
                liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                        sellerId, List.of(LiveStatus.LIVE, LiveStatus.READY));

        Live broadcasting = lives.stream().filter(Live::isBroadcasting).findFirst().orElse(null);
        List<Live> scheduled = lives.stream().filter(live -> !live.isBroadcasting()).toList();

        Map<Long, Integer> productCounts =
                productService.countScheduledProducts(scheduled.stream().map(Live::getId).toList());

        return SellerLiveTabResponse.of(
                broadcasting,
                scheduled.stream()
                        .map(
                                live ->
                                        SellerLiveTabResponse.Scheduled.of(
                                                live, productCounts.getOrDefault(live.getId(), 0)))
                        .toList());
    }

    @Transactional(readOnly = true)
    public LiveViewerResponse getByPublicId(String publicId) {
        Live live = findByPublicId(publicId);
        return LiveViewerResponse.of(live, userService.findSellerProfile(live.getSellerId()));
    }

    public BroadcastCredentialResponse reissueCredential(Long liveId, Long sellerId) {
        Live live = requireOwnLive(liveId, sellerId);
        if (live.isEnded()) {
            throw new CustomException(LiveErrorCode.LIVE_ALREADY_ENDED);
        }
        return BroadcastCredentialResponse.from(
                liveStreamingClient.reissueCredential(live.getIvsChannelArn()));
    }

    public LiveStreamStatusResponse getStreamStatus(Long liveId, Long sellerId) {
        Live live = requireOwnLive(liveId, sellerId);
        StreamStatus streamStatus = liveStreamingClient.getStreamStatus(live.getIvsChannelArn());
        if (streamStatus.isBroadcasting() && !live.isEnded()) {
            live = syncToBroadcasting(live);
        }
        return LiveStreamStatusResponse.of(live, streamStatus.state());
    }

    /** 시청 화면이 시청자 수를 주기적으로 읽는다. */
    // 값이 있는데 갱신을 선점하지 못했으면 다른 요청이 최근에 물어본 것이라 직전 값을 그대로 준다.
    // 그래야 만료 순간에 몰린 요청이 저마다 IVS를 부르지 않는다. IVS를 부르므로 트랜잭션으로 묶지 않는다.
    public LiveViewerCountResponse getViewerCount(String publicId) {
        Optional<Integer> cached = liveViewerCountRepository.find(publicId);
        if (cached.isPresent() && !liveViewerCountRepository.tryStartRefresh(publicId)) {
            return new LiveViewerCountResponse(cached.get());
        }
        return new LiveViewerCountResponse(fetchAndCacheViewerCount(publicId));
    }

    // 끝난 방송만 막고 나머지는 IVS에 묻는다. lives.status는 셀러의 송출 상태 조회로만 갱신돼서,
    // 그걸로 막으면 영상은 나가는데 시청자 수만 0으로 내려가는 구간이 생긴다.
    private int fetchAndCacheViewerCount(String publicId) {
        Live live = findByPublicId(publicId);
        int viewerCount =
                live.isEnded()
                        ? 0
                        : liveStreamingClient
                                .getStreamStatus(live.getIvsChannelArn())
                                .viewerCount();
        liveViewerCountRepository.save(publicId, viewerCount);
        return viewerCount;
    }

    /** 시청자가 라이브 화면에서 상품 바와 전체 상품 시트를 채운다. */
    @Transactional(readOnly = true)
    public LiveProductsResponse getPublicLiveProducts(String publicId) {
        Long liveId = findByPublicId(publicId).getId();
        return productService.findLiveProducts(liveId);
    }

    @Transactional(readOnly = true)
    public LivePlaybackResponse getPlayback(String publicId) {
        return LivePlaybackResponse.from(findByPublicId(publicId));
    }

    public LiveDetailResponse end(Long liveId, Long sellerId) {
        Live live = requireOwnLive(liveId, sellerId);
        if (!live.isEnded()) {
            liveStreamingClient.stopStream(live.getIvsChannelArn());
            liveStreamingClient.deleteStreamKeys(live.getIvsChannelArn());
            live.end();
            liveRepository.save(live);
        }
        // 이미 끝난 라이브에도 태운다. 상품 정리가 실패했을 때 종료를 다시 호출해 보정할 수 있어야 한다.
        productService.closeLiveSales(liveId);
        return LiveDetailResponse.from(live);
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

    // 사진 복사와 채널 생성보다 앞에서 끊는다. 뒤에 두면 거부할 요청도 S3와 IVS를 먼저 건드린다.
    private void requireScheduleRoom(Long sellerId) {
        if (liveRepository.countBySellerIdAndStatus(sellerId, LiveStatus.READY)
                >= Live.MAX_SCHEDULED) {
            throw new CustomException(LiveErrorCode.LIVE_SCHEDULE_LIMIT_EXCEEDED);
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

    // 채널과 채팅방이 같이 쓴다. 둘 중 빡빡한 IVS 채널명 규칙([a-zA-Z0-9-_], 128자)에 맞춘다.
    private String generateResourceName(Long sellerId) {
        return "toasty-live-" + sellerId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 시청자가 채팅방에 붙을 때 쓸 토큰을 발급한다. */
    // 비로그인 시청자도 읽을 수 있어야 해서 viewer가 null로 들어온다. 그때는 쓰기 권한 없이 발급한다.
    // IVS를 부르므로 트랜잭션으로 묶지 않는다.
    public LiveChatTokenResponse issueChatToken(String publicId, AuthUser viewer) {
        Live live = findByPublicId(publicId);
        if (live.getIvsChatRoomArn() == null) {
            throw new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_NOT_FOUND);
        }
        ChatTokenCommand command = toChatTokenCommand(live, viewer);
        return LiveChatTokenResponse.of(liveChatClient.createToken(command), command.writable());
    }

    // 채팅 참여자 번호는 방 안에서만 겹치지 않으면 된다. 비로그인은 매번 새로 만든다.
    // 끝난 방송에는 쓰기를 열지 않는다. 방을 회수하기 전까지 남은 시청자가 읽을 수 있어야 해서 막지는 않는다.
    private ChatTokenCommand toChatTokenCommand(Live live, AuthUser viewer) {
        if (viewer == null) {
            return new ChatTokenCommand(
                    live.getIvsChatRoomArn(),
                    "guest-" + UUID.randomUUID(),
                    null,
                    ChatRole.GUEST,
                    false);
        }
        boolean owner = live.isOwnedBy(viewer.sellerId());
        return new ChatTokenCommand(
                live.getIvsChatRoomArn(),
                "user-" + viewer.userId(),
                userService.findNickname(viewer.userId()),
                owner ? ChatRole.SELLER : ChatRole.CUSTOMER,
                !live.isEnded());
    }

    /** 종료된 지 오래된 라이브의 채팅방을 회수한다. */
    // 라이브 삭제는 방송 전에만 되므로, 한 번이라도 방송된 라이브의 방은 이 배치로만 지워진다.
    // IVS를 부르므로 트랜잭션으로 묶지 않는다. 지우지 못한 방은 ARN을 남겨 다음 차례에 다시 시도한다.
    public void cleanUpEndedChatRooms() {
        List<Live> targets =
                liveRepository.findByStatusAndEndedAtBeforeAndIvsChatRoomArnIsNotNull(
                        LiveStatus.ENDED, LocalDateTime.now().minus(CHAT_ROOM_RETENTION));
        for (Live live : targets) {
            reclaimChatRoom(live);
        }
    }

    private void reclaimChatRoom(Live live) {
        try {
            liveChatClient.deleteRoom(live.getIvsChatRoomArn());
        } catch (RuntimeException e) {
            log.error(
                    "채팅방이 회수되지 않았습니다 - liveId={}, chatRoomArn={}",
                    live.getId(),
                    live.getIvsChatRoomArn(),
                    e);
            return;
        }
        live.clearChatRoom();
        liveRepository.save(live);
    }

    // 생성 보상에서도 삭제 뒤 정리에서도 부른다. 정리가 실패해도 요청을 뒤집지 않고 로그만 남긴다.
    private void deleteChatRoomQuietly(String chatRoomArn) {
        // 이 기능 이전에 만들어진 라이브에는 방이 없다.
        if (chatRoomArn == null) {
            return;
        }
        try {
            liveChatClient.deleteRoom(chatRoomArn);
        } catch (RuntimeException e) {
            log.error("채팅방이 정리되지 않았습니다 - chatRoomArn={}", chatRoomArn, e);
        }
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
