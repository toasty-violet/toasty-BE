package com.toasty.domain.product.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "PRODUCT_IMAGE_REQUIRED", "상품 사진은 필수입니다."),
    PRODUCT_IMAGE_NOT_UPLOADED(
            HttpStatus.BAD_REQUEST, "PRODUCT_IMAGE_NOT_UPLOADED", "업로드가 끝나지 않은 사진이 있습니다."),
    PRODUCT_IMAGE_FORBIDDEN(
            HttpStatus.FORBIDDEN, "PRODUCT_IMAGE_FORBIDDEN", "본인이 올린 사진만 등록할 수 있습니다."),
    PRODUCT_UPLOAD_URL_ISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "PRODUCT_UPLOAD_URL_ISSUE_FAILED", "사진 업로드 주소 발급에 실패했습니다.");

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
