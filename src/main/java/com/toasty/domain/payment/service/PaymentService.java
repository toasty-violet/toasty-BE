package com.toasty.domain.payment.service;

import com.toasty.domain.payment.client.Point3PaymentClient;
import com.toasty.domain.payment.client.dto.PaymentSessionCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentSessionResponse;
import com.toasty.domain.payment.controller.dto.response.PayerIdSessionResponse;
import com.toasty.domain.payment.entity.Payment;
import com.toasty.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // 결제자 식별값(payerId)을 받는 것이 목적인 세션이라 금액과 상품명을 고정한다.
    private static final int PAYER_ID_SESSION_AMOUNT = 100;
    private static final String PAYER_ID_SESSION_PRODUCT_NAME = "toasty 계좌 등록";

    private final Point3PaymentClient point3PaymentClient;
    private final PaymentRepository paymentRepository;

    /** payerId를 받기 위한 결제 세션을 만들고 프론트가 결제창을 열 때 쓸 세션 아이디를 반환한다. */
    public PayerIdSessionResponse createPayerIdSession(Long userId) {
        PaymentSessionResponse session =
                point3PaymentClient.createSession(
                        new PaymentSessionCreateRequest(
                                PAYER_ID_SESSION_AMOUNT, PAYER_ID_SESSION_PRODUCT_NAME));
        // 승인 단계에서 세션의 목적을 되찾을 수 있도록 세션 아이디를 내려주기 전에 남긴다.
        paymentRepository.save(Payment.createForPayerId(userId, session.id()));
        log.info("payerId 확보용 결제 세션 생성 - userId={}, sessionId={}", userId, session.id());
        return new PayerIdSessionResponse(session.id());
    }
}
