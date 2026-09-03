package com.toasty.global.exception;

import com.toasty.global.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * {@link ResponseEntityExceptionHandler} 상속을 빼면 404/405/415와 잘못된 JSON 본문이 전부 500으로 나간다.
 *
 * <p>부모가 이미 처리하는 예외(MethodArgumentNotValidException 등)에 {@code @ExceptionHandler}를 새로 붙이면 매핑이 중복되어
 * 기동에 실패한다. 그런 예외는 아래처럼 부모 메서드를 오버라이드한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 의도된 실패이므로 스택트레이스 없이 warn으로만 남긴다. */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        Throwable cause = e.getCause();
        if (cause != null) {
            log.warn("[{}] {} | cause: {}", errorCode.code(), e.getMessage(), cause.getMessage());
        } else {
            log.warn("[{}] {}", errorCode.code(), e.getMessage());
        }
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.fail(ErrorResponse.of(errorCode)));
    }

    /** 인가 애노테이션이 막은 요청 — 로그인하지 않았으면 401, 권한이 부족하면 403으로 내려준다. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(
            AuthorizationDeniedException e) {
        ErrorCode errorCode =
                isAuthenticated() ? CommonErrorCode.FORBIDDEN : CommonErrorCode.UNAUTHORIZED;
        log.warn("[{}] {}", errorCode.code(), e.getMessage());
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.fail(ErrorResponse.of(errorCode)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.status())
                .body(ApiResponse.fail(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR)));
    }

    /** {@code @Valid} 검증 실패 — 어떤 필드가 틀렸는지 응답에 담는다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ErrorResponse.FieldError> fields =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ErrorResponse.FieldError(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.code(), fields);
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT.status())
                .body(ApiResponse.fail(ErrorResponse.of(CommonErrorCode.INVALID_INPUT, fields)));
    }

    /** 나머지 MVC 표준 예외 — 상태코드는 프레임워크 판단을 쓰고 본문만 감싼다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ErrorCode errorCode = toErrorCode(statusCode);
        log.warn("[{}] {}", errorCode.code(), ex.getMessage());
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ApiResponse.fail(ErrorResponse.of(errorCode)));
    }

    // 로그인한 요청인지 확인한다
    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private ErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.equals(HttpStatus.NOT_FOUND)) {
            return CommonErrorCode.NOT_FOUND;
        }
        if (statusCode.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return CommonErrorCode.METHOD_NOT_ALLOWED;
        }
        if (statusCode.is4xxClientError()) {
            return CommonErrorCode.INVALID_INPUT;
        }
        return CommonErrorCode.INTERNAL_SERVER_ERROR;
    }
}
