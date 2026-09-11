package com.toasty.domain.product.entity;

/** 셀러 상품탭 위의 상태 칩. 다 팔린 상품은 목록에 뜨지 않아 고를 수 없다. */
public enum SellerProductFilter {
    ALL(null),
    ON_SALE(SalesType.GENERAL),
    SCHEDULED(SalesType.LIVE);

    // 상품탭에서 빼는 상태. 전체 칩도 이것만 걸러 낸다.
    public static final SalesType EXCLUDED = SalesType.SOLD_OUT;

    private final SalesType salesType;

    SellerProductFilter(SalesType salesType) {
        this.salesType = salesType;
    }

    public boolean isAll() {
        return salesType == null;
    }

    /** 전체 칩이면 null이다. 그때는 EXCLUDED를 빼는 쪽으로 읽는다. */
    public SalesType salesType() {
        return salesType;
    }
}
