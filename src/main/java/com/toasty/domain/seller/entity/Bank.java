package com.toasty.domain.seller.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* 판매자가 정산 계좌로 등록할 수 있는 은행. */
@Getter
@RequiredArgsConstructor
public enum Bank {
    KAKAO_BANK("카카오뱅크"),
    NONGHYEOP("농협"),
    SHINHAN("신한"),
    IBK("IBK기업"),
    HANA("하나"),
    WOORI("우리"),
    KOOKMIN("국민"),
    SC_JEIL("SC제일"),
    IM_BANK("iM뱅크(대구)"),
    BUSAN("부산"),
    GWANGJU("광주"),
    SAEMAUL_GEUMGO("새마을금고"),
    GYEONGNAM("경남"),
    JEONBUK("전북"),
    JEJU("제주"),
    KDB("산업"),
    POST_OFFICE("우체국"),
    SHINHYEOP("신협"),
    SUHYEOP("수협"),
    CITI("씨티"),
    K_BANK("케이뱅크"),
    TOSS_BANK("토스뱅크"),
    SANLIM_JOHAP("산림조합"),
    SAVINGS_BANK("저축은행");

    private final String label;
}
