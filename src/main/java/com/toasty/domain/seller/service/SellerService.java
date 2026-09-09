package com.toasty.domain.seller.service;

import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.entity.SellerShop;
import com.toasty.domain.seller.exception.SellerErrorCode;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.global.config.SellerS3Properties;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final SellerS3Properties s3Properties;

    /** 온보딩 제출로 판매자 정보를 만든다. 유저의 역할 확정과 같은 트랜잭션에서 일어난다. */
    @Transactional
    public Seller createForOnboarding(SellerOnboardingCommand command) {
        requireShopImageOwnedByUser(command.userId(), command.shopImageObjectKey());
        requireBusinessNumberNotRegistered(command.businessNumber());
        return sellerRepository.save(Seller.createForOnboarding(command));
    }

    /** 다른 도메인이 화면에 스토어를 표시할 때 쓴다. */
    // seller_name은 대표자 실명이라 내보내지 않는다. 화면에 뜨는 스토어 이름은 users.nickname이다.
    @Transactional(readOnly = true)
    public SellerShop findShop(Long sellerId) {
        Seller seller =
                sellerRepository
                        .findById(sellerId)
                        .orElseThrow(() -> new CustomException(SellerErrorCode.SELLER_NOT_FOUND));
        return new SellerShop(
                seller.getId(), seller.getUserId(), toImageUrl(seller.getShopImageObjectKey()));
    }

    // 온보딩에서 샵 이미지를 받지만, 값이 비어 있어도 화면이 기본 이미지를 그리도록 null을 그대로 넘긴다.
    private String toImageUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return s3Properties.publicBaseUrl() + "/" + objectKey;
    }

    // 업로드 주소를 발급할 때 키에 넣은 유저 번호로, 남의 사진을 자기 스토어에 붙이는 것을 막는다.
    private void requireShopImageOwnedByUser(Long userId, String objectKey) {
        if (!objectKey.startsWith(s3Properties.imagePrefix() + userId + "/")) {
            throw new CustomException(SellerErrorCode.SELLER_SHOP_IMAGE_FORBIDDEN);
        }
    }

    // 사업자등록번호는 선택 입력이라 값이 있을 때만 본다.
    private void requireBusinessNumberNotRegistered(String businessNumber) {
        if (businessNumber != null && sellerRepository.existsByBusinessNumber(businessNumber)) {
            throw new CustomException(SellerErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED);
        }
    }
}
