package com.toasty.domain.seller.service;

import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
import com.toasty.domain.seller.controller.dto.response.ShopNameSearchResponse;
import com.toasty.domain.seller.controller.dto.response.ShopNameSuggestionResponse;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.exception.SellerErrorCode;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.global.config.SellerS3Properties;
import com.toasty.global.exception.CustomException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerService {

    // 저장이 실패했을 때 어느 값이 겹쳤는지 가르는 데 쓴다
    private static final String BUSINESS_NUMBER_CONSTRAINT = "uk_sellers_business_number";
    private static final String SHOP_NAME_CONSTRAINT = "uk_sellers_shop_name";

    // 추천 스토어 이름은 이 셋을 이어 붙여 만든다. 가장 긴 조합도 스토어 이름 한도인 20자를 넘지 않는다
    private static final List<String> SHOP_NAME_MODIFIERS =
            List.of("골목", "새벽", "언덕", "구름", "동네", "햇살", "오후", "모퉁이");
    private static final List<String> SHOP_NAME_NOUNS =
            List.of("빵집", "제과", "베이커리", "제빵소", "공방", "오븐", "상회", "브레드");
    private static final int SHOP_NAME_SUFFIX_ORIGIN = 100;
    private static final int SHOP_NAME_SUFFIX_BOUND = 1000;

    // 이만큼 뽑아도 다 겹치면 조합을 포기하고 겹치지 않을 값으로 내려준다
    private static final int SHOP_NAME_SUGGESTION_ATTEMPTS = 10;
    private static final String FALLBACK_SHOP_NAME_PREFIX = "shop_";

    private final SellerRepository sellerRepository;
    private final SellerS3Properties s3Properties;

    /** 온보딩 제출로 판매자 정보를 만든다. 유저의 역할 확정과 같은 트랜잭션에서 일어난다. */
    @Transactional
    public Seller createForOnboarding(SellerOnboardingCommand command) {
        requireShopImageOwnedByUser(command.userId(), command.shopImageObjectKey());
        requireShopNameAvailable(command.shopName(), null);
        requireBusinessNumberNotRegistered(command.businessNumber());
        return saveOrThrowDuplicated(Seller.createForOnboarding(command));
    }

    /** 다른 도메인이 화면에 스토어를 표시할 때 쓴다. */
    // seller_name은 대표자 실명이라 내보내지 않는다. 화면에 뜨는 이름은 shop_name이다.
    @Transactional(readOnly = true)
    public SellerProfileResponse findShopProfile(Long sellerId) {
        Seller seller = findSeller(sellerId);
        return new SellerProfileResponse(
                seller.getId(), seller.getShopName(), toImageUrl(seller.getShopImageObjectKey()));
    }

    /** 입력한 스토어 이름을 이미 다른 판매자가 쓰고 있는지 확인한다. 자기 이름을 그대로 둔 경우는 중복으로 보지 않는다. */
    @Transactional(readOnly = true)
    public ShopNameSearchResponse searchShopName(String shopName, Long sellerId) {
        return new ShopNameSearchResponse(isShopNameTaken(shopName, sellerId));
    }

    /** 온보딩 화면의 스토어 이름 입력창에 채워 둘 값을 만들어 준다. */
    // 저장하지 않으므로 유저가 제출하기 전에 다른 판매자가 먼저 쓸 수 있다. 그 경우는 온보딩 제출에서 중복으로 걸린다.
    @Transactional(readOnly = true)
    public ShopNameSuggestionResponse suggestShopName() {
        for (int attempt = 0; attempt < SHOP_NAME_SUGGESTION_ATTEMPTS; attempt++) {
            String candidate = randomShopName();
            if (!sellerRepository.existsByShopName(candidate)) {
                return new ShopNameSuggestionResponse(candidate);
            }
        }
        return new ShopNameSuggestionResponse(randomFallbackShopName());
    }

    /** 판매자가 탈퇴할 때 스토어 이름을 놓아준다. 판매자 정보 자체는 거래 상대방 식별에 쓰여 남긴다. */
    @Transactional
    public void withdraw(Long sellerId) {
        findSeller(sellerId).withdraw();
    }

    private Seller findSeller(Long sellerId) {
        return sellerRepository
                .findById(sellerId)
                .orElseThrow(() -> new CustomException(SellerErrorCode.SELLER_NOT_FOUND));
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

    // sellerId가 null이면 아직 판매자가 아닌 유저라 비교에서 뺄 자기 자신이 없다.
    private boolean isShopNameTaken(String shopName, Long sellerId) {
        return sellerId == null
                ? sellerRepository.existsByShopName(shopName)
                : sellerRepository.existsByShopNameAndIdNot(shopName, sellerId);
    }

    private void requireShopNameAvailable(String shopName, Long sellerId) {
        if (isShopNameTaken(shopName, sellerId)) {
            throw new CustomException(SellerErrorCode.SELLER_SHOP_NAME_DUPLICATED);
        }
    }

    // 사업자등록번호는 선택 입력이라 값이 있을 때만 본다.
    private void requireBusinessNumberNotRegistered(String businessNumber) {
        if (businessNumber != null && sellerRepository.existsByBusinessNumber(businessNumber)) {
            throw new CustomException(SellerErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED);
        }
    }

    // 판매자를 DB에 바로 넣어, 검사와 저장 사이에 다른 판매자가 같은 값을 선점했으면 중복(409)으로 돌려준다.
    private Seller saveOrThrowDuplicated(Seller seller) {
        try {
            return sellerRepository.saveAndFlush(seller);
        } catch (DataIntegrityViolationException e) {
            throw duplicatedOrOriginal(e);
        }
    }

    // 스토어 이름과 사업자등록번호가 한 번에 들어가 둘 다 제약 위반이 날 수 있어, 제약 이름으로 가른다.
    // 판매자 행에는 uk_sellers_user_id도 걸려 있어, 둘 중 어느 쪽도 아니면 여기서 판단하지 않고 그대로 올려보낸다.
    private static RuntimeException duplicatedOrOriginal(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return e;
        }
        if (message.contains(BUSINESS_NUMBER_CONSTRAINT)) {
            return new CustomException(SellerErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED, e);
        }
        if (message.contains(SHOP_NAME_CONSTRAINT)) {
            return new CustomException(SellerErrorCode.SELLER_SHOP_NAME_DUPLICATED, e);
        }
        return e;
    }

    private String randomShopName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return SHOP_NAME_MODIFIERS.get(random.nextInt(SHOP_NAME_MODIFIERS.size()))
                + SHOP_NAME_NOUNS.get(random.nextInt(SHOP_NAME_NOUNS.size()))
                + random.nextInt(SHOP_NAME_SUFFIX_ORIGIN, SHOP_NAME_SUFFIX_BOUND);
    }

    private String randomFallbackShopName() {
        return FALLBACK_SHOP_NAME_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
