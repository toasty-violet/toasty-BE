package com.toasty.domain.live.controller;

import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.annotation.SellerOnly;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.live.controller.dto.request.LiveCreateRequest;
import com.toasty.domain.live.controller.dto.request.LiveProductUpdateRequest;
import com.toasty.domain.live.controller.dto.request.LiveUpdateRequest;
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
import com.toasty.domain.live.service.LiveService;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.entity.LiveProductPinCommand;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Live", description = "라이브 방송 API")
@RestController
@RequestMapping("/api/v1/lives")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService liveService;

    @Operation(
            summary = "라이브 생성",
            description =
                    "셀러가 새 라이브를 개설하면서 이번 방송에서 팔 상품을 함께 등록합니다. 라이브 설정 화면에서 방송 저장을 누를 때 한 번"
                            + " 호출하세요. 상품 사진은 먼저 업로드 주소를 발급받아 S3에 올린 뒤 그 objectKey를 넣습니다. 보낸 상품"
                            + " 순서가 그대로 라이브 내 노출 순서가 됩니다. 송출정보는 이 응답에 없습니다 — 송출 직전에 재발급"
                            + " API로 받으세요. 아직 방송하지 않은 라이브를 "
                            + Live.MAX_SCHEDULED
                            + "개까지 가지고 있을 수 있고, 그보다 많으면 409로 거부됩니다.")
    @SellerOnly
    @PostMapping
    public ApiResponse<LiveWithProductsResponse> create(
            @Valid @RequestBody LiveCreateRequest request, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.create(request.toCommand(seller.sellerId())));
    }

    @Operation(
            summary = "셀러 라이브탭 조회",
            description =
                    "셀러가 라이브탭에 들어올 때 자기 라이브 상황을 한 번에 가져옵니다. 화면 진입 시 한 번 호출하세요. 지금 방송 중인"
                            + " 라이브(없으면 null), 예정된 라이브 목록(방송 예정 시각 오름차순), 최신 라이브 현황 세 가지를 함께"
                            + " 내려줍니다. 종료된 라이브는 담기지 않습니다. latestStat은 주문·시청자 집계가 아직 없어 항상"
                            + " null이니 값이 없는 화면을 그리세요. 방송 중 라이브의 판매율도 같은 이유로 0으로 나갑니다.")
    @SellerOnly
    @GetMapping("/me")
    public ApiResponse<SellerLiveTabResponse> getMyLiveTab(@LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.getMyLiveTab(seller.sellerId()));
    }

    @Operation(
            summary = "라이브 상세 조회",
            description =
                    "셀러가 자기 라이브 하나를 편성 상품까지 가져옵니다. 라이브 수정 화면에 들어갈 때 한 번 호출해 폼을 채우세요."
                            + " 응답이 라이브 생성 응답과 같은 형태라 생성 폼과 같은 방식으로 다루면 됩니다. 상품은 노출 순서대로"
                            + " 내려갑니다. 방송 중이거나 종료된 라이브도 조회할 수 있고, 고칠 수 있는지는 수정 API가 판단합니다."
                            + " 본인의 라이브만 조회할 수 있으며 송출정보는 포함하지 않습니다.")
    @SellerOnly
    @GetMapping("/{liveId}")
    public ApiResponse<LiveWithProductsResponse> getMyLiveDetail(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.getMyLiveDetail(liveId, seller.sellerId()));
    }

    @Operation(
            summary = "라이브 수정",
            description =
                    "셀러가 방송 시작 전에 라이브 내용과 판매할 상품을 고칩니다. 라이브 수정 화면에서 저장을 누를 때 호출하세요. 보낸 필드만"
                            + " 바뀌고 보내지 않은 필드는 그대로 유지됩니다. products는 부분 수정이 아니라 전체 교체입니다 —"
                            + " 보낸 배열에 없는 상품은 편성에서 빠지고 삭제되며, 배열 순서가 그대로 노출 순서가 됩니다. 상품을"
                            + " 건드리지 않으려면 products를 아예 빼고 보내세요. 새 상품은 productId 없이 보내고 사진의"
                            + " objectKey를 함께 넣으며, 기존 상품의 사진을 바꿀 때만 objectKey를 넣습니다. 방송이 시작된"
                            + " 뒤에는 시청자가 보고 있는 정보라 수정할 수 없고, 본인의 라이브만 수정할 수 있습니다.")
    @SellerOnly
    @PatchMapping("/{liveId}")
    public ApiResponse<LiveDetailResponse> update(
            @PathVariable Long liveId,
            @Valid @RequestBody LiveUpdateRequest request,
            @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.update(request.toCommand(liveId, seller.sellerId())));
    }

    @Operation(
            summary = "라이브 삭제",
            description =
                    "셀러가 방송 시작 전에 저장해둔 라이브를 지웁니다. 라이브 목록이나 수정 화면에서 삭제를 누를 때 호출하세요. 편성된 상품과"
                            + " 사진, 송출 채널까지 함께 지워지고 되돌릴 수 없습니다. 다만 다른 라이브에도 편성된 상품은 남습니다."
                            + " 방송이 시작된 뒤에는 지난 방송 페이지가 남아야 해서 지울 수 없고, 본인의 라이브만 지울 수 있습니다.")
    @SellerOnly
    @DeleteMapping("/{liveId}")
    public ApiResponse<Void> delete(@PathVariable Long liveId, @LoginUser AuthUser seller) {
        liveService.delete(liveId, seller.sellerId());
        return ApiResponse.ok();
    }

    @Operation(
            summary = "방송 중 전체 상품 조회",
            description =
                    "셀러가 방송 화면에서 전체 상품 시트를 열 때 호출합니다. 편성한 순서대로 내려가고,"
                            + " currentPinnedProductId가 지금 소개 중인 상품입니다. 아직 아무것도 고정하지 않았으면"
                            + " null입니다. 한 번 고정한 상품은 다른 상품을 고정해도 계속 구매 가능한 상태로 남습니다.")
    @SellerOnly
    @GetMapping("/{liveId}/products")
    public ApiResponse<LiveProductsResponse> getMyLiveProducts(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.getMyLiveProducts(liveId, seller.sellerId()));
    }

    @Operation(
            summary = "방송 중 상품 고정",
            description =
                    "셀러가 지금 소개할 상품을 고정합니다. 고정한 상품은 시청자에게 구매 버튼이 열리고, 다른 상품을 고정해도"
                            + " 계속 구매할 수 있습니다. 옮겨가는 것은 화면에 표시되는 '현재 고정 상품'뿐입니다."
                            + " 방송 중일 때만 호출할 수 있고, 품절된 상품은 고정할 수 없습니다.")
    @SellerOnly
    @PatchMapping("/{liveId}/products/{productId}/pin")
    public ApiResponse<Void> pinProduct(
            @PathVariable Long liveId, @PathVariable Long productId, @LoginUser AuthUser seller) {
        liveService.pinProduct(new LiveProductPinCommand(liveId, productId, seller.sellerId()));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "방송 중 상품 가격·재고 수정",
            description =
                    "셀러가 방송 중에 상품의 가격과 재고를 고칩니다. 상품 수정 시트에서 저장을 누를 때 호출하세요."
                            + " 방송 중에는 상품을 추가하거나 뺄 수 없어 가격과 재고만 바꿉니다. 상품명과 사진은"
                            + " 방송 전에 정해집니다.")
    @SellerOnly
    @PatchMapping("/{liveId}/products/{productId}")
    public ApiResponse<Void> changeProductPriceAndStock(
            @PathVariable Long liveId,
            @PathVariable Long productId,
            @Valid @RequestBody LiveProductUpdateRequest request,
            @LoginUser AuthUser seller) {
        liveService.changeProductPriceAndStock(
                request.toCommand(liveId, productId, seller.sellerId()));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "송출정보 재발급",
            description =
                    "셀러가 방송 송출에 필요한 정보를 새로 발급받습니다. 송출 직전에 호출하세요. 기존 스트림 키는 즉시 폐기되며, 새 송출정보는 이 응답에서만"
                            + " 전달되어 다시 조회할 수 없습니다.")
    @SellerOnly
    @PostMapping("/{liveId}/broadcast-credentials")
    public ApiResponse<BroadcastCredentialResponse> reissueCredential(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.reissueCredential(liveId, seller.sellerId()));
    }

    @Operation(
            summary = "방송 종료",
            description =
                    "셀러가 진행 중인 라이브를 종료합니다. 송출이 중단되고 스트림 키가 삭제되어 다시 송출할 수 없으며, 채널과 재생 URL은 지난 방송"
                            + " 페이지를 위해 유지됩니다. 편성했던 상품은 일반 판매로 넘어가 방송이 끝난 뒤에도 계속 살 수 있습니다."
                            + " 본인의 라이브만 종료할 수 있습니다.")
    @SellerOnly
    @PostMapping("/{liveId}/end")
    public ApiResponse<LiveDetailResponse> end(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.end(liveId, seller.sellerId()));
    }

    @Operation(
            summary = "송출 상태 조회",
            description =
                    "셀러가 자신의 송출이 실제로 시작됐는지 확인합니다. 셀러의 송출 대기 화면에서만 폴링하세요. 요청마다 IVS를 호출하므로 시청자 화면에서"
                        + " 쓰면 시청자 수만큼 호출이 늘어납니다(시청자에게는 재생 정보 조회 API를 쓰세요). IVS의 실제 송출 여부를 조회해 라이브"
                        + " 상태를 맞추며, 송출이 확인되면 LIVE로 전이됩니다. 본인의 라이브만 조회할 수 있습니다.")
    @SellerOnly
    @GetMapping("/{liveId}/stream-status")
    public ApiResponse<LiveStreamStatusResponse> getStreamStatus(
            @PathVariable Long liveId, @LoginUser AuthUser seller) {
        return ApiResponse.ok(liveService.getStreamStatus(liveId, seller.sellerId()));
    }

    @Operation(
            summary = "재생 정보 조회",
            description =
                    "시청자가 방송이 시작됐는지 확인하고 재생을 시작합니다. 시청자 대기 화면에서 이 API를 폴링하고, status가 LIVE가 되면"
                            + " playbackUrl로 재생을 시작하세요. 경로의 publicId는 시청 화면 진입에 쓴 값을 그대로 사용합니다."
                            + " 저장된 상태만 읽어 IVS를 호출하지 않으므로 시청자가 많아도 부담이 없으며, 송출정보(streamKey,"
                            + " ingestEndpoint)는 포함하지 않습니다.")
    @GetMapping("/public/{publicId}/playback")
    public ApiResponse<LivePlaybackResponse> getPlayback(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getPlayback(publicId));
    }

    @Operation(
            summary = "라이브 시청 - 시청자 수 조회",
            description =
                    "시청 화면 상단의 시청자 수를 채웁니다. 값이 계속 바뀌므로 시청 중에 주기적으로 호출하세요."
                            + " 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다. 송출 전이면 0이고, 끝난 방송도 0입니다."
                            + " 서버가 30초마다 갱신해 둔 값을 읽어 주므로 그보다 자주 불러도 같은 값이 나옵니다.")
    @GetMapping("/public/{publicId}/viewer-count")
    public ApiResponse<LiveViewerCountResponse> getViewerCount(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getViewerCount(publicId));
    }

    @Operation(
            summary = "라이브 시청 - 상품 조회",
            description =
                    "시청자가 라이브 화면의 하단 상품 바와 전체 상품 시트를 채울 때 호출합니다. 시청 화면 진입 시 한 번 부르고,"
                            + " 셀러가 상품을 바꿀 수 있으므로 시트를 열 때마다 다시 부르세요. 인증이 필요 없어 비로그인 유저도"
                            + " 호출할 수 있습니다. 편성한 순서대로 내려가며 전체 상품 개수는 products의 길이입니다."
                            + " currentPinnedProductId가 지금 소개 중인 상품이고, 아직 아무것도 고정하지 않았으면 null입니다."
                            + " 구매 버튼은 status가 ACTIVE인 상품에만 열리고, 그중 stockQuantity가 0이면 품절로 막습니다."
                            + " status가 SCHEDULED면 아직 소개 전이라 버튼을 띄우지 않습니다. 방송 전이나 종료 뒤에도"
                            + " 호출할 수 있고, 방송 전에는 모든 상품이 SCHEDULED라 구매 버튼이 열리지 않습니다.")
    @GetMapping("/public/{publicId}/products")
    public ApiResponse<LiveProductsResponse> getPublicLiveProducts(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getPublicLiveProducts(publicId));
    }

    @Operation(
            summary = "라이브 시청 - 채팅 입장 토큰 발급",
            description =
                    "시청 화면이 채팅에 붙을 때 호출합니다. 받은 token을 IVS Chat SDK에 넘기세요."
                            + " 인증이 필요 없어 비로그인 유저도 호출할 수 있고, 그때는 writable이 false로 내려가"
                            + " 읽기만 됩니다. 로그인 뒤 다시 호출하면 메시지를 보낼 수 있는 토큰을 받습니다."
                            + " 끝난 방송도 방이 남아 있는 동안은 읽을 수 있지만 writable은 false입니다."
                            + " expiresAt은 접속한 세션이 유지되는 시각입니다. 그 전에 다시 호출해 새 토큰을 받으세요."
                            + " 메시지에 실려 오는 role이 SELLER면 방송을 진행하는 셀러가 보낸 것이고,"
                            + " displayName이 화면에 띄울 이름입니다. 채팅방이 없는 라이브면 404가 납니다.")
    @PostMapping("/public/{publicId}/chat-token")
    public ApiResponse<LiveChatTokenResponse> issueChatToken(
            @PathVariable String publicId, @LoginUser AuthUser viewer) {
        return ApiResponse.ok(liveService.issueChatToken(publicId, viewer));
    }

    @Operation(
            summary = "라이브 시청",
            description =
                    "유저가 라이브 시청 화면에 들어올 때 필요한 정보를 가져옵니다. 시청 화면 진입 시 한 번 호출하세요. 인증이 필요 없어"
                            + " 비로그인 유저도 호출할 수 있습니다. 경로의 publicId는 라이브 생성 응답으로 받은 값이며, 순차 liveId를"
                            + " 시청 화면 URL에 노출하지 않기 위해 공개 조회는 이 값만 받습니다. 재생에 필요한 playbackUrl과 라이브"
                            + " 정보, 화면 상단에 띄울 셀러의 스토어 이름과 대표 이미지를 반환합니다. 시청자 수는 계속 바뀌므로 여기 담지"
                            + " 않고 시청자 수 조회에서 따로 받습니다. 송출정보(streamKey, ingestEndpoint)는 포함하지 않습니다.")
    @GetMapping("/public/{publicId}")
    public ApiResponse<LiveViewerResponse> getByPublicId(@PathVariable String publicId) {
        return ApiResponse.ok(liveService.getByPublicId(publicId));
    }
}
