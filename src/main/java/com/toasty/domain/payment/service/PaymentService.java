package com.toasty.domain.payment.service;

import com.toasty.domain.payment.client.Point3PaymentClient;
import com.toasty.domain.payment.client.dto.PaymentSessionCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentSessionResponse;
import com.toasty.domain.payment.controller.dto.response.PayerIdSessionResponse;
import com.toasty.domain.payment.entity.Payment;
import com.toasty.domain.payment.exception.PaymentErrorCode;
import com.toasty.domain.payment.repository.PaymentRepository;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 계좌 등록을 마친 세션에서 유저의 payerId를 꺼낸다. */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 검증을 빠뜨릴 수 없도록 소유자 확인을 함께 한다.
    public String getVerifiedPayerId(Long userId, String sessionId) {
        validatePayerIdSessionOwner(userId, sessionId);
        PaymentSessionResponse session = point3PaymentClient.getSession(sessionId);
        if (!session.hasPayerId()) {
            log.warn(
                    "본인인증 전 세션으로 payerId 조회 - userId={}, sessionId={}, status={}",
                    userId,
                    sessionId,
                    session.status());
            throw new CustomException(PaymentErrorCode.PAYMENT_PAYER_ID_NOT_READY);
        }
        return session.payerId();
    }

    /** 프론트가 보낸 세션 아이디가 이 유저의 payerId 확보용 세션인지 확인한다. */
    // 남의 세션 아이디를 붙여 보내는 것을 막는다.
    @Transactional(readOnly = true)
    public void validatePayerIdSessionOwner(Long userId, String sessionId) {
        Payment payment =
                paymentRepository
                        .findBySessionId(sessionId)
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                PaymentErrorCode.PAYMENT_SESSION_NOT_FOUND));
        if (!payment.isPayerIdSessionOf(userId)) {
            log.warn(
                    "결제 세션 소유자 불일치 - userId={}, sessionId={}, ownerId={}",
                    userId,
                    sessionId,
                    payment.getUserId());
            throw new CustomException(PaymentErrorCode.PAYMENT_SESSION_OWNER_MISMATCH);
        }
    }
}
