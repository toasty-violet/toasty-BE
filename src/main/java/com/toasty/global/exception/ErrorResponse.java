package com.toasty.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 실패 응답 본문. {@code fields}는 @Valid 검증 실패 시에만 채운다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> fields) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fields) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), fields);
    }

    public record FieldError(String field, String message) {}
}
