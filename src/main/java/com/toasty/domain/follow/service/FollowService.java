package com.toasty.domain.follow.service;

import com.toasty.domain.follow.controller.dto.response.TopStoreResponse;
import com.toasty.domain.follow.entity.Follow;
import com.toasty.domain.follow.repository.FollowRepository;
import com.toasty.domain.follow.repository.FollowerCount;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import com.toasty.domain.seller.service.SellerService;
import java.util.List;
import java.util.Map;
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

    // 비로그인 유저와 판매자 계정은 customerId가 없어 팔로우한 스토어를 조회하지 않는다.
    private Set<Long> findFollowedSellerIds(Long customerId, List<Long> sellerIds) {
        if (customerId == null) {
            return Set.of();
        }
        return Set.copyOf(followRepository.findFollowedSellerIds(customerId, sellerIds));
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
