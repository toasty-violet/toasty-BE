package com.toasty.domain.seller.entity;

/** 샵 이미지 업로드 주소 발급 요청값. 온보딩 전에도 발급받으므로 셀러가 아닌 유저 번호로 식별한다. */
public record ShopImageUploadCommand(Long userId, String contentType, long contentLength) {}
