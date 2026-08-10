package com.toasty.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus status();

    String code();

    /** 클라이언트에 그대로 노출되므로 내부 사정을 담지 않는다. */
    String message();
}
