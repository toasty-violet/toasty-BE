package com.toasty.domain.seller.service;

import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
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
        return sellerRepository.save(
                Seller.createForOnboarding(command, toImageUrl(command.shopImageObjectKey())));
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

    private String toImageUrl(String objectKey) {
        return s3Properties.publicBaseUrl() + "/" + objectKey;
    }
}
