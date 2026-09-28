package com.toasty.domain.product.entity;

/** 주문이 재고를 선점한 상품. 상품은 나중에 값이 바뀌거나 지워지므로 주문에 복사할 값을 함께 담는다. */
// liveId는 방송에서 팔린 상품일 때만 채워진다. 스토어나 상품 상세에서 산 주문은 비어 있다.
public record ReservedProduct(
        Long productId, Long sellerId, String name, int price, String imageUrl, Long liveId) {}
