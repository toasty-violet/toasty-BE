package com.toasty.domain.seller.service;

import com.toasty.domain.follow.service.FollowService;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.seller.controller.dto.response.ShopResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 본인의 스토어 관리 화면. 팔로워 수와 상품 수를 함께 봐야 해서 세 도메인을 여기서 맞춘다. */
// FollowService가 스토어를 표시하려고 이미 SellerService를 보고 있어, SellerService에서 거꾸로 부르면 순환이 된다.
@Service
@RequiredArgsConstructor
public class SellerShopService {

    private final SellerService sellerService;
    private final FollowService followService;
    private final ProductService productService;

    /** 판매자 본인의 스토어 관리 화면을 채운다. */
    @Transactional(readOnly = true)
    public ShopResponse findMyShop(Long sellerId) {
        return ShopResponse.of(
                sellerService.findMyShopDetail(sellerId),
                followService.countFollowers(sellerId),
                productService.countBySeller(sellerId));
    }
}
