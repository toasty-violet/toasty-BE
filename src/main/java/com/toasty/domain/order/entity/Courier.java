package com.toasty.domain.order.entity;

/** 운송장에 붙는 택배사. 셀러 화면의 택배사 드롭다운도 이 목록으로 채운다. */
public enum Courier {
    CJ_LOGISTICS("CJ 대한통운"),
    HANJIN("한진택배"),
    LOTTE("롯데택배"),
    LOGEN("로젠택배"),
    POST_OFFICE("우체국택배");

    private final String displayName;

    Courier(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
