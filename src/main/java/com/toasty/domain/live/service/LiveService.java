package com.toasty.domain.live.service;

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.customer.service.CustomerService;
import com.toasty.domain.live.client.LiveChatClient;
import com.toasty.domain.live.client.LiveStreamingClient;
import com.toasty.domain.live.client.dto.ChatRole;
import com.toasty.domain.live.client.dto.ChatTokenCommand;
import com.toasty.domain.live.client.dto.StreamStatus;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.HomeLiveResponse;
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
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.entity.LiveProductPinCommand;
import com.toasty.domain.product.entity.LiveProductUpdateCommand;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveService {

    private static final int HOME_LIVE_LIMIT = 5;

    // 시청자 수나 예정 시각이 같아도 순서가 흔들리지 않게 id를 뒤에 둔다.
    // id 방향은 인덱스를 읽는 방향과 맞춰 정렬을 따로 하지 않게 잡았다.
    private static final Sort BROADCASTING_ORDER =
            Sort.by(Sort.Order.desc("viewerCount"), Sort.Order.desc("id"));

    private static final Sort SCHEDULED_ORDER =
            Sort.by(Sort.Order.asc("scheduledAt"), Sort.Order.asc("id"));

    // 방송이 끝나도 결제 시트에 남아 있는 시청자가 있어, 이만큼 지난 뒤에 채팅방을 회수한다.
    private static final Duration CHAT_ROOM_RETENTION = Duration.ofMinutes(30);

    // 셀러당 예정 라이브가 Live.MAX_SCHEDULED로 묶여 있어 배치가 도는 양에 상한이 있다.
    private static final List<LiveStatus> UNFINISHED_STATUSES =
            List.of(LiveStatus.READY, LiveStatus.LIVE);

    private final LiveRepository liveRepository;
    private final LiveStreamingClient liveStreamingClient;
    private final LiveChatClient liveChatClient;
    private final ProductService productService;
    private final SellerService sellerService;
    private final CustomerService customerService;
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
        return LiveViewerResponse.of(live, sellerService.findShopProfile(live.getSellerId()));
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
            live = syncToBroadcasting(live, streamStatus.viewerCount());
        }
        return LiveStreamStatusResponse.of(live, streamStatus.state());
    }

    /** 시청 화면이 시청자 수를 주기적으로 읽는다. */
    // 배치가 적어둔 값을 읽기만 한다. 시청자가 몇 명이든 IVS 호출은 늘지 않는다.
    @Transactional(readOnly = true)
    public LiveViewerCountResponse getViewerCount(String publicId) {
        return new LiveViewerCountResponse(findByPublicId(publicId).getViewerCount());
    }

    /** 아직 끝나지 않은 라이브의 시청자 수를 IVS에서 읽어 적어둔다. */
    // 셀러가 송출 상태를 조회하지 않아도 방송이 잡히도록 예정까지 살펴, 송출 중이면 상태도 함께 올린다.
    // IVS 왕복은 트랜잭션 밖에서 끝내고, 값이 바뀐 라이브만 짧은 트랜잭션 하나에 몰아 쓴다.
    public void refreshViewerCounts() {
        Map<Long, Integer> viewerCounts = new LinkedHashMap<>();
        List<Long> startedLiveIds = new ArrayList<>();
        for (Live live : liveRepository.findByStatusIn(UNFINISHED_STATUSES)) {
            StreamStatus streamStatus = readStreamStatus(live);
            if (streamStatus == null) {
                continue;
            }
            if (streamStatus.isBroadcasting() && !live.isBroadcasting()) {
                startedLiveIds.add(live.getId());
                viewerCounts.put(live.getId(), streamStatus.viewerCount());
            } else if (live.isBroadcasting()
                    && streamStatus.viewerCount() != live.getViewerCount()) {
                viewerCounts.put(live.getId(), streamStatus.viewerCount());
            }
        }
        if (viewerCounts.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(
                status -> applyViewerCounts(viewerCounts, startedLiveIds));
    }

    // 한 라이브가 실패해도 나머지는 갱신한다.
    private StreamStatus readStreamStatus(Live live) {
        try {
            return liveStreamingClient.getStreamStatus(live.getIvsChannelArn());
        } catch (RuntimeException e) {
            log.warn("송출 상태를 읽지 못했습니다 - liveId={}", live.getId(), e);
            return null;
        }
    }

    // 상태를 올릴 라이브만 엔티티로 다뤄 시청자 수까지 함께 반영하고, 나머지는 UPDATE 한 번씩으로 끝낸다.
    private void applyViewerCounts(Map<Long, Integer> viewerCounts, List<Long> startedLiveIds) {
        for (Live live : liveRepository.findAllById(startedLiveIds)) {
            Integer viewerCount = viewerCounts.remove(live.getId());
            if (startBroadcastQuietly(live)) {
                live.updateViewerCount(viewerCount);
            }
        }
        viewerCounts.forEach(liveRepository::updateViewerCount);
    }

    // 한 셀러가 두 라이브를 동시에 송출하면 나머지 갱신까지 멈추므로, 그 라이브만 건너뛴다.
    private boolean startBroadcastQuietly(Live live) {
        try {
            live.startBroadcast();
            liveRepository.flush();
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("이미 방송 중인 셀러라 상태를 올리지 못했습니다 - liveId={}", live.getId(), e);
            return false;
        }
    }

    /** 넘긴 라이브 중 방송 중인 것이 있는지 알려준다. 무엇을 막을지는 물어본 쪽이 정한다. */
    @Transactional(readOnly = true)
    public boolean hasBroadcasting(Collection<Long> liveIds) {
        return !liveIds.isEmpty() && liveRepository.existsByIdInAndStatus(liveIds, LiveStatus.LIVE);
    }

    /** 넘긴 라이브 중 아직 방송하지 않은 것만 추려 준다. */
    @Transactional(readOnly = true)
    public List<Long> filterScheduled(Collection<Long> liveIds) {
        if (liveIds.isEmpty()) {
            return List.of();
        }
        return liveRepository.findIdsByIdInAndStatus(liveIds, LiveStatus.READY);
    }

    /** 홈 화면의 라이브 섹션을 채운다. */
    // 셀러 정보는 라이브마다 조회하지 않고 한 번에 모아 읽는다.
    @Transactional(readOnly = true)
    public List<HomeLiveResponse> findHomeLives() {
        List<Live> lives = new ArrayList<>(broadcastingForHome());
        if (lives.size() < HOME_LIVE_LIMIT) {
            lives.addAll(scheduledForHome(HOME_LIVE_LIMIT - lives.size()));
        }
        if (lives.isEmpty()) {
            return List.of();
        }
        Map<Long, SellerProfileResponse> sellers =
                sellerService.findShopProfiles(
                        lives.stream().map(Live::getSellerId).distinct().toList());
        return lives.stream()
                .map(live -> HomeLiveResponse.of(live, sellers.get(live.getSellerId())))
                .toList();
    }

    private List<Live> broadcastingForHome() {
        return liveRepository.findByStatus(
                LiveStatus.LIVE, PageRequest.of(0, HOME_LIVE_LIMIT, BROADCASTING_ORDER));
    }

    // 방송 중이 자리를 다 채웠으면 예정은 읽지 않는다.
    private List<Live> scheduledForHome(int limit) {
        return liveRepository.findByStatusAndScheduledAtGreaterThanEqual(
                LiveStatus.READY, LocalDateTime.now(), PageRequest.of(0, limit, SCHEDULED_ORDER));
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

    // 방송 시작을 기록하면서 시청자 수도 함께 적는다. 여기 값이 없으면 배치가 처음 돌 때까지 0으로 보인다.
    // 이미 부른 응답에서 꺼내 쓰므로 IVS 호출은 늘지 않는다.
    private Live syncToBroadcasting(Live live, int viewerCount) {
        if (live.isBroadcasting()) {
            return live;
        }
        live.startBroadcast();
        live.updateViewerCount(viewerCount);
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
                findChatDisplayName(viewer),
                owner ? ChatRole.SELLER : ChatRole.CUSTOMER,
                !live.isEnded());
    }

    // 채팅에 뜨는 이름은 역할에 따라 다른 곳에 있다. 판매자는 스토어 이름, 구매자는 닉네임을 쓴다.
    private String findChatDisplayName(AuthUser viewer) {
        if (viewer.sellerId() != null) {
            return sellerService.findShopProfile(viewer.sellerId()).shopName();
        }
        if (viewer.customerId() != null) {
            return customerService.findNickname(viewer.customerId());
        }
        return null;
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
