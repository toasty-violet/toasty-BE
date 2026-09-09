package com.toasty.domain.live.client;

import java.io.IOException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.RetryableException;

/**
 * 잠시 후 재시도하면 성공할 수 있는 실패인지 판정한다. SdkException.retryable()은 RetryableException 외에는 전부 false를 반환해 신뢰할
 * 수 없으므로 타입과 원인 사슬로 판정한다.
 */
// 같은 이름의 예외가 서비스마다 다른 클래스라, 서비스별 타입은 부르는 쪽이 넘긴다.
final class TransientFailures {

    private static final int MAX_CAUSE_DEPTH = 10;

    private TransientFailures() {}

    static boolean isTransient(Throwable e, Class<?>... serviceSpecificTypes) {
        if (isAnyOf(e, serviceSpecificTypes)
                || e instanceof ApiCallTimeoutException
                || e instanceof ApiCallAttemptTimeoutException
                || e instanceof RetryableException) {
            return true;
        }
        // 네트워크 오류와 자격증명 실패가 둘 다 SdkClientException으로 오므로 타입으로 구분되지 않는다.
        // 네트워크 쪽만 원인 사슬에 IOException을 달고 온다.
        Throwable cause = e.getCause();
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isAnyOf(Throwable e, Class<?>... types) {
        for (Class<?> type : types) {
            if (type.isInstance(e)) {
                return true;
            }
        }
        return false;
    }
}
