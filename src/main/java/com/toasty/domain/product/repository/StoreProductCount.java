package com.toasty.domain.product.repository;

/** 스토어별 상품 수. 스토어마다 세지 않고 한 번에 묶어 가져올 때 쓴다. */
public interface StoreProductCount {

    Long getSellerId();

    int getProductCount();
}
