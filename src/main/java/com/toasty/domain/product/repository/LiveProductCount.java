package com.toasty.domain.product.repository;

/** 라이브별 편성 상품 수. 라이브마다 세지 않고 한 번에 묶어 가져올 때 쓴다. */
public interface LiveProductCount {

    Long getLiveId();

    int getProductCount();
}
