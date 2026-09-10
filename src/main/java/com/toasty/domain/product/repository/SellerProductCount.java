package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.SalesType;

/** 상태별 상품 수. 상태마다 세지 않고 한 번에 묶어 가져올 때 쓴다. */
public interface SellerProductCount {

    SalesType getSalesType();

    int getProductCount();
}
