package com.toasty.domain.product.service;

import com.toasty.domain.live.service.LiveService;
import com.toasty.domain.product.entity.SellerProductUpdateCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.global.exception.CustomException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 셀러 상품탭의 수정과 삭제. 라이브 상태를 함께 봐야 해서 두 도메인을 여기서 맞춘다. */
// LiveService가 이미 ProductService를 보고 있어, ProductService에서 거꾸로 부르면 순환이 된다.
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final ProductService productService;
    private final LiveService liveService;
    private final TransactionTemplate transactionTemplate;

    /** 상품탭에서 상품 하나를 고친다. */
    public void update(SellerProductUpdateCommand command) {
        requireEditable(command.productId(), command.sellerId());

        List<String> permanentKeys =
                productService.copySellerImagesToPermanent(
                        command.sellerId(), command.imageObjectKeys());

        List<String> obsoleteImageKeys = new ArrayList<>();
        try {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        // 사진 복사가 도는 동안 송출이 시작됐을 수 있어 트랜잭션 안에서 다시 본다.
                        requireEditable(command.productId(), command.sellerId());
                        obsoleteImageKeys.addAll(
                                productService.updateSellerProduct(command, permanentKeys));
                    });
        } catch (RuntimeException e) {
            productService.deleteCopiedImagesQuietly(command.imageObjectKeys(), permanentKeys);
            throw e;
        }

        // 여기서 실패해도 되돌리지 않는다. 이미 커밋돼서 새 사진은 DB가 참조하고 있다.
        productService.deleteImagesQuietly(obsoleteImageKeys);
    }

    /** 상품탭에서 상품 하나를 지운다. 편성돼 있던 라이브에서도 함께 빠진다. */
    public void delete(Long productId, Long sellerId) {
        List<String> obsoleteImageKeys =
                transactionTemplate.execute(
                        status -> {
                            List<Long> liveIds = requireEditable(productId, sellerId);
                            // 끝난 라이브의 편성은 세지 않는다. 이미 지난 방송이 빌 일은 없다.
                            if (productService.hasLiveWithSingleProduct(
                                    liveService.filterScheduled(liveIds))) {
                                throw new CustomException(ProductErrorCode.PRODUCT_LAST_IN_LIVE);
                            }
                            return productService.deleteSellerProduct(productId, sellerId);
                        });

        // 커밋된 뒤에 지운다. 먼저 지우면 트랜잭션이 깨졌을 때 사진을 되살릴 수 없다.
        productService.deleteImagesQuietly(obsoleteImageKeys);
    }

    // 방송 중인 라이브에 편성돼 있으면 라이브 화면에서만 고칠 수 있다.
    private List<Long> requireEditable(Long productId, Long sellerId) {
        List<Long> liveIds = productService.findScheduledLiveIds(productId, sellerId);
        if (liveService.hasBroadcasting(liveIds)) {
            throw new CustomException(ProductErrorCode.PRODUCT_BROADCASTING);
        }
        return liveIds;
    }
}
