package com.toasty.domain.product.entity;

import java.util.List;

/** 셀러 상품탭 위의 상태 칩. 다 팔린 상품은 목록에 뜨지 않아 고를 수 없다. */
public enum SellerProductFilter {
    ALL(List.of(SalesType.LIVE, SalesType.GENERAL)),
    ON_SALE(List.of(SalesType.GENERAL)),
    SCHEDULED(List.of(SalesType.LIVE));

    private final List<SalesType> salesTypes;

    SellerProductFilter(List<SalesType> salesTypes) {
        this.salesTypes = salesTypes;
    }

    public List<SalesType> salesTypes() {
        return salesTypes;
    }
}
