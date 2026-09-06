package com.toasty.domain.payment.client;

import com.toasty.domain.payment.client.dto.PaymentSessionCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentSessionResponse;
import com.toasty.domain.payment.exception.PaymentErrorCode;
import com.toasty.global.config.Point3Properties;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** point3 결제 API를 호출한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class Point3PaymentClient {

    private static final String SESSION_PATH = "/payment/v3/session";

    private final Point3Properties point3Properties;
    private final RestClient restClient = RestClient.create();

    /** 결제창을 열기 전에 결제 세션을 만든다. */
    public PaymentSessionResponse createSession(PaymentSessionCreateRequest request) {
        try {
            return restClient
                    .post()
                    .uri(point3Properties.baseUrl() + SESSION_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + point3Properties.apiToken())
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
}
