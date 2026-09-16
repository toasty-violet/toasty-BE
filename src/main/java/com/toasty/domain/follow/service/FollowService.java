package com.toasty.domain.follow.service;

import com.toasty.domain.follow.controller.dto.response.FollowingStoreResponse;
import com.toasty.domain.follow.controller.dto.response.TopStoreResponse;
import com.toasty.domain.follow.entity.Follow;
import com.toasty.domain.follow.repository.FollowRepository;
import com.toasty.domain.follow.repository.FollowerCount;
import com.toasty.domain.product.controller.dto.response.StoreProductResponse;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import com.toasty.domain.seller.service.SellerService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final int TOP_STORE_LIMIT = 3;

    private static final int FOLLOWING_STORE_LIMIT = 3;

    private final FollowRepository followRepository;
    private final SellerService sellerService;
    private final ProductService productService;

    /** 홈 화면의 인기 스토어 섹션을 채운다. */
    // 스토어 정보와 상품 수는 스토어마다 조회하지 않고 각각 한 번에 모아 읽는다.
    @Transactional(readOnly = true)
    public List<TopStoreResponse> findTopStores(Long customerId) {
        List<FollowerCount> ranked =
                followRepository.findSellerIdsOrderByFollowerCountDesc(
                        PageRequest.of(0, TOP_STORE_LIMIT));
        if (ranked.isEmpty()) {
            return List.of();
        }
        List<Long> sellerIds = ranked.stream().map(FollowerCount::getSellerId).toList();

        Map<Long, SellerProfileResponse> shops = sellerService.findShopProfiles(sellerIds);
        Map<Long, Integer> productCounts = productService.countStoreProducts(sellerIds);
        Set<Long> followed = findFollowedSellerIds(customerId, sellerIds);

        return ranked.stream()
                .map(
                        count ->
                                TopStoreResponse.of(
                                        shops.get(count.getSellerId()),
                                        count.getFollowerCount(),
                                        productCounts.getOrDefault(count.getSellerId(), 0),
                                        followed.contains(count.getSellerId())))
                .toList();
    }

    /** 홈 화면의 팔로우하는 스토어 섹션을 채운다. */
    // 미리보기 상품은 스토어마다 조회하지 않고 한 번에 모아 읽는다.
    // 판매중 상품이 없는 스토어도 자리를 지키므로, 상품이 빈 배열로 내려갈 수 있다.
    @Transactional(readOnly = true)
    public List<FollowingStoreResponse> findFollowingStores(Long customerId) {
        List<SellerProfileResponse> shops = findStoresToShow(customerId);
        if (shops.isEmpty()) {
            return List.of();
        }

        Map<Long, List<StoreProductResponse>> products =
                productService.findStoreProductPreviews(
                        shops.stream().map(SellerProfileResponse::sellerId).toList());

        return shops.stream()
                .map(
                        shop ->
                                FollowingStoreResponse.of(
                                        shop, products.getOrDefault(shop.sellerId(), List.of())))
                .toList();
    }

    // 비로그인 유저와 판매자 계정은 customerId가 없고, 구매자도 아직 아무도 팔로우하지 않았을 수 있다.
    // 그때도 섹션이 비지 않도록 먼저 만들어진 스토어로 채운다.
    private List<SellerProfileResponse> findStoresToShow(Long customerId) {
        if (customerId == null) {
            return sellerService.findEarliestShopProfiles(FOLLOWING_STORE_LIMIT);
        }
        List<Long> sellerIds =
                followRepository.findFollowingSellerIds(
                        customerId, PageRequest.of(0, FOLLOWING_STORE_LIMIT));
        if (sellerIds.isEmpty()) {
            return sellerService.findEarliestShopProfiles(FOLLOWING_STORE_LIMIT);
        }

        // 최근 팔로우한 순서를 지켜 다시 세운다. 조회 사이에 탈퇴한 스토어는 자리에서 뺀다.
        Map<Long, SellerProfileResponse> shops = sellerService.findShopProfiles(sellerIds);
        return sellerIds.stream().map(shops::get).filter(Objects::nonNull).toList();
    }

    /** 구매자가 스토어를 팔로우한다. 이미 팔로우 중이면 그대로 성공으로 본다. */
    @Transactional
    public void follow(Long customerId, Long sellerId) {
        sellerService.requireSellerExists(sellerId);
        if (followRepository.existsByCustomerIdAndSellerId(customerId, sellerId)) {
            return;
        }
        saveIgnoringDuplicated(Follow.create(customerId, sellerId));
    }

    /** 구매자가 스토어 팔로우를 취소한다. 팔로우 중이 아니어도 성공으로 본다. */
    @Transactional
    public void unfollow(Long customerId, Long sellerId) {
        followRepository.deleteByCustomerIdAndSellerId(customerId, sellerId);
    }

    /** 다른 도메인이 화면에 판매자의 팔로워 수를 표시할 때 쓴다. */
    @Transactional(readOnly = true)
    public long countFollowers(Long sellerId) {
        return followRepository.countBySellerId(sellerId);
    }

    /** 다른 도메인이 상세 화면에서 팔로우 버튼의 초기 상태를 채울 때 쓴다. */
    // 비로그인 유저와 판매자 계정은 customerId가 없어 팔로우한 적이 없다.
    @Transactional(readOnly = true)
    public boolean isFollowing(Long customerId, Long sellerId) {
        return customerId != null
                && followRepository.existsByCustomerIdAndSellerId(customerId, sellerId);
    }

    /** 다른 도메인이 목록 화면에서 카드마다 팔로우 버튼의 초기 상태를 채울 때 쓴다. */
    // 비로그인 유저와 판매자 계정은 customerId가 없어 팔로우한 스토어를 조회하지 않는다.
    @Transactional(readOnly = true)
    public Set<Long> findFollowedSellerIds(Long customerId, List<Long> sellerIds) {
        if (customerId == null || sellerIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(followRepository.findFollowedSellerIds(customerId, sellerIds));
    }

    /** 구매자가 탈퇴할 때 그가 판매자를 팔로우한 관계를 모두 지운다. */
    @Transactional
    public void deleteByCustomerId(Long customerId) {
        followRepository.deleteAllByCustomerId(customerId);
    }

    /** 판매자가 탈퇴할 때 구매자가 그를 팔로우한 관계를 모두 지운다. */
    @Transactional
    public void deleteBySellerId(Long sellerId) {
        followRepository.deleteAllBySellerId(sellerId);
    }

    // 팔로우 버튼을 연타하면 검사와 저장 사이에 같은 쌍이 먼저 들어갈 수 있다.
    // 그때 걸리는 유니크 제약은 이미 팔로우됐다는 뜻이라 실패로 돌려주지 않는다.
    private void saveIgnoringDuplicated(Follow follow) {
        try {
            followRepository.saveAndFlush(follow);
        } catch (DataIntegrityViolationException ignored) {
            // 원하는 상태가 이미 만들어져 있어 할 일이 없다.
        }
    }
}
