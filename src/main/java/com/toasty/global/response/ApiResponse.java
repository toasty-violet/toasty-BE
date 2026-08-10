package com.toasty.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.global.exception.ErrorResponse;

/** 팩토리 이름이 {@code success}가 아니라 {@code ok}인 이유: record 접근자 {@code success()}와 겹치면 컴파일되지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
