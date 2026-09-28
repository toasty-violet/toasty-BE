package com.toasty.domain.payment.client;

import com.toasty.domain.payment.client.dto.PaymentCaptureResponse;
import com.toasty.domain.payment.client.dto.PaymentRefundCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentRefundResponse;
import com.toasty.domain.payment.client.dto.PaymentRefundStatusResponse;
import com.toasty.domain.payment.client.dto.PaymentSessionCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentSessionResponse;
import com.toasty.domain.payment.exception.PaymentErrorCode;
import com.toasty.global.config.Point3Properties;
import com.toasty.global.exception.CustomException;
import com.toasty.global.exception.ErrorCode;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** point3 결제 API를 호출한다. */
// 승인과 취소는 멱등해서, 응답을 받지 못한 요청만 같은 내용으로 다시 보낸다.
// 세션 생성은 멱등하지 않아 다시 보내지 않는다. 다시 보내면 결제창만 여러 개 생긴다.
@Slf4j
@Component
public class Point3PaymentClient {

    private static final String SESSION_PATH = "/payment/v3/session";
    private static final String CAPTURE_PATH = "/capture/v2/";
    private static final String REFUND_PATH = "/refunds/v1/";
    private static final String RESUME_SUFFIX = "/resume";

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 500L;

    // 같은 취소 요청을 두 번 접수시키지 않으려고 붙인다. point3가 24시간 동안 기억한다.
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // 취소 요청이 409로 막힌 이유를 point3가 응답 본문 코드로 알려준다.
    private static final String DUPLICATE_REQUEST = "REFUND_DUPLICATE_REQUEST";
    private static final String ACTIVE_REQUEST_EXISTS = "REFUND_ACTIVE_REQUEST_EXISTS";
    private static final String EOB_WINDOW_BLOCKED = "EOB_WINDOW_BLOCKED";

    private final Point3Properties point3Properties;
    private final RestClient restClient;

    public Point3PaymentClient(Point3Properties point3Properties) {
        this.point3Properties = point3Properties;
        this.restClient = RestClient.builder().requestFactory(timeoutOf(point3Properties)).build();
    }

    /** 결제창을 열기 전에 결제 세션을 만든다. */
    public PaymentSessionResponse createSession(PaymentSessionCreateRequest request) {
        try {
            return restClient
                    .post()
                    .uri(point3Properties.baseUrl() + SESSION_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PaymentSessionResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("point3 결제 세션 생성 일시 실패 - amount={}", request.amount(), e);
            throw new CustomException(PaymentErrorCode.PAYMENT_TEMPORARILY_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("point3 결제 세션 생성 실패 - amount={}", request.amount(), e);
            throw new CustomException(PaymentErrorCode.PAYMENT_SESSION_CREATE_FAILED, e);
        }
    }

    /** 결제창을 통과한 세션의 상태와 payerId를 확인한다. */
    // 승인 결과를 받지 못했을 때 실제 상태를 가리는 데도 쓴다. 조회는 부수효과가 없어 재시도한다.
    public PaymentSessionResponse getSession(String sessionId) {
        return callWithRetry(
                () ->
                        restClient
                                .get()
                                .uri(point3Properties.baseUrl() + SESSION_PATH + "/" + sessionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .retrieve()
                                .body(PaymentSessionResponse.class),
                "결제 세션 조회",
                sessionId,
                PaymentErrorCode.PAYMENT_SESSION_QUERY_FAILED);
    }

    /** 결제 세션을 승인해 결제금액이 빠져나가게 한다. */
    // 202도 정상 응답이라 예외가 아니다. 결과는 본문 status로 가른다.
    public PaymentCaptureResponse capture(String sessionId) {
        return callWithRetry(
                () ->
                        restClient
                                .post()
                                .uri(point3Properties.baseUrl() + CAPTURE_PATH + sessionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .retrieve()
                                .body(PaymentCaptureResponse.class),
                "결제 승인",
                sessionId,
                PaymentErrorCode.PAYMENT_CAPTURE_FAILED);
    }

    /** 승인이 끝난 결제를 취소한다. 부분 취소도 이 API로 한다. */
    public PaymentRefundResponse createRefund(
            String sessionId, PaymentRefundCreateRequest request, String idempotencyKey) {
        return callWithRetry(
                () -> {
                    try {
                        return restClient
                                .post()
                                .uri(point3Properties.baseUrl() + REFUND_PATH + sessionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(request)
                                .retrieve()
                                .body(PaymentRefundResponse.class);
                    } catch (HttpClientErrorException.Conflict e) {
                        throw blockedReasonOf(e, sessionId);
                    }
                },
                "결제 취소",
                sessionId,
                PaymentErrorCode.PAYMENT_REFUND_FAILED);
    }

    /** 원결제 금액과 남은 취소 가능 금액, 지금까지의 취소 내역을 확인한다. */
    public PaymentRefundStatusResponse getRefundStatus(String sessionId) {
        return callWithRetry(
                () ->
                        restClient
                                .get()
                                .uri(point3Properties.baseUrl() + REFUND_PATH + sessionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .retrieve()
                                .body(PaymentRefundStatusResponse.class),
                "결제 취소 상태 조회",
                sessionId,
                PaymentErrorCode.PAYMENT_REFUND_STATUS_QUERY_FAILED);
    }

    /** 처리 중으로 멈춘 취소를 이어서 처리하게 한다. */
    public PaymentRefundStatusResponse resumeRefund(String sessionId) {
        return callWithRetry(
                () ->
                        restClient
                                .post()
                                .uri(
                                        point3Properties.baseUrl()
                                                + REFUND_PATH
                                                + sessionId
                                                + RESUME_SUFFIX)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .retrieve()
                                .body(PaymentRefundStatusResponse.class),
                "결제 취소 재개",
                sessionId,
                PaymentErrorCode.PAYMENT_REFUND_FAILED);
    }

    /**
     * 응답을 받지 못한 요청만 같은 내용으로 다시 보낸다.
     *
     * <p>끝까지 받지 못하면 실패가 아니라 {@code PAYMENT_RESULT_UNKNOWN}으로 올린다. 호출한 쪽이 조회로 실제 상태를 가려야 한다.
     */
    // 4xx는 요청 자체가 잘못됐다는 뜻이라 다시 보내지 않는다.
    private <T> T callWithRetry(
            Supplier<T> call, String action, String sessionId, ErrorCode failure) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn(
                            "point3 {} 결과를 확인하지 못했다 - sessionId={}, attempts={}",
                            action,
                            sessionId,
                            attempt,
                            e);
                    throw new CustomException(PaymentErrorCode.PAYMENT_RESULT_UNKNOWN, e);
                }
                log.warn("point3 {} 재시도 - sessionId={}, attempt={}", action, sessionId, attempt, e);
                sleepBeforeRetry();
            } catch (CustomException e) {
                throw e;
            } catch (Exception e) {
                log.error("point3 {} 실패 - sessionId={}", action, sessionId, e);
                throw new CustomException(failure, e);
            }
        }
    }

    /**
     * 취소가 409로 막힌 이유를 응답 본문에서 가린다.
     *
     * <p>이유를 읽지 못하면 결과를 알 수 없는 것으로 보고 조회에 맡긴다. 접수된 요청을 실패로 확정하면 취소가 조용히 사라진다.
     */
    // 이미 접수된 요청(중복 키)도 결과를 모르는 것으로 본다. 상태 조회가 실제 처리 결과를 알려준다.
    private CustomException blockedReasonOf(HttpClientErrorException.Conflict e, String sessionId) {
        String body = e.getResponseBodyAsString();
        log.warn("point3 결제 취소가 막혔다 - sessionId={}, body={}", sessionId, body);
        if (body.contains(ACTIVE_REQUEST_EXISTS)) {
            return new CustomException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS, e);
        }
        if (body.contains(EOB_WINDOW_BLOCKED)) {
            return new CustomException(PaymentErrorCode.PAYMENT_REFUND_WINDOW_BLOCKED, e);
        }
        if (body.contains(DUPLICATE_REQUEST)) {
            return new CustomException(PaymentErrorCode.PAYMENT_RESULT_UNKNOWN, e);
        }
        return new CustomException(PaymentErrorCode.PAYMENT_RESULT_UNKNOWN, e);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(PaymentErrorCode.PAYMENT_RESULT_UNKNOWN, e);
        }
    }

    private String bearer() {
        return "Bearer " + point3Properties.apiToken();
    }

    private static ClientHttpRequestFactory timeoutOf(Point3Properties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
