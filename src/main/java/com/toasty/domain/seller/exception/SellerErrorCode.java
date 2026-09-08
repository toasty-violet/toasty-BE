package com.toasty.domain.seller.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SellerErrorCode implements ErrorCode {
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "판매자를 찾을 수 없습니다."),
    SELLER_UPLOAD_URL_ISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "SELLER_UPLOAD_URL_ISSUE_FAILED", "사진 업로드 주소 발급에 실패했습니다."),

    // 다른 유저가 발급받은 샵 이미지 키로 온보딩을 제출한 경우
    SELLER_SHOP_IMAGE_FORBIDDEN(
            HttpStatus.FORBIDDEN, "SELLER_SHOP_IMAGE_FORBIDDEN", "본인이 올린 사진이 아닙니다."),

    // 다른 판매자가 이미 등록한 사업자등록번호로 온보딩을 제출한 경우
    SELLER_BUSINESS_NUMBER_DUPLICATED(
            HttpStatus.CONFLICT, "SELLER_BUSINESS_NUMBER_DUPLICATED", "이미 등록된 사업자등록번호입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
