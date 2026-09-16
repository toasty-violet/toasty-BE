package com.toasty.domain.payment.service;

import com.toasty.domain.payment.client.Point3PaymentClient;
import com.toasty.domain.payment.client.dto.PaymentCaptureResponse;
import com.toasty.domain.payment.client.dto.PaymentRefundCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentRefundResponse;
import com.toasty.domain.payment.client.dto.PaymentRefundStatusResponse;
import com.toasty.domain.payment.client.dto.PaymentSessionCreateRequest;
import com.toasty.domain.payment.client.dto.PaymentSessionResponse;
import com.toasty.domain.payment.controller.dto.response.PayerIdSessionResponse;
import com.toasty.domain.payment.entity.Payment;
import com.toasty.domain.payment.entity.PaymentCaptureResult;
import com.toasty.domain.payment.entity.PaymentPurpose;
import com.toasty.domain.payment.entity.PaymentRefundCommand;
import com.toasty.domain.payment.entity.PurchaseSessionCommand;
import com.toasty.domain.payment.entity.Refund;
import com.toasty.domain.payment.exception.PaymentErrorCode;
import com.toasty.domain.payment.repository.PaymentRepository;
import com.toasty.domain.payment.repository.RefundRepository;
import com.toasty.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // 결제자 식별값(payerId)을 받는 것이 목적인 세션이라 금액과 상품명을 고정한다.
    private static final int PAYER_ID_SESSION_AMOUNT = 100;
    private static final String PAYER_ID_SESSION_PRODUCT_NAME = "toasty 계좌 등록";

    // point3가 세션을 만들 때 쓰는 부가세 계산식이다. 취소는 계산해 주지 않아 같은 식으로 맞춘다.
    private static final double VAT_DIVISOR = 11.0;

    // 면세 상품을 팔지 않아 취소 금액 전부를 과세로 본다.
    private static final int NO_TAX_FREE_AMOUNT = 0;

    private final Point3PaymentClient point3PaymentClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final TransactionTemplate transactionTemplate;

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
        Payment payment = requirePayment(sessionId);
        if (!payment.isPayerIdSessionOf(userId)) {
            log.warn(
                    "결제 세션 소유자 불일치 - userId={}, sessionId={}, ownerId={}",
                    userId,
                    sessionId,
                    payment.getUserId());
            throw new CustomException(PaymentErrorCode.PAYMENT_SESSION_OWNER_MISMATCH);
        }
    }

    /** 상품 결제용 세션을 만들고 프론트가 결제창을 열 때 쓸 세션 아이디를 반환한다. */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 금액은 호출한 쪽이 상품에서 계산한 값이어야 한다.
    public String createPurchaseSession(PurchaseSessionCommand command) {
        PaymentSessionResponse session =
                point3PaymentClient.createSession(
                        new PaymentSessionCreateRequest(command.amount(), command.productName()));
        transactionTemplate.executeWithoutResult(
                status -> paymentRepository.save(Payment.createForPurchase(command, session.id())));
        log.info(
                "상품 결제 세션 생성 - orderId={}, sessionId={}, amount={}",
                command.orderId(),
                session.id(),
                command.amount());
        return session.id();
    }

    /**
     * 결제 세션을 승인한다. 이미 승인된 세션은 그 결과를 그대로 돌려준다.
     *
     * <p>결과를 가리지 못하면 {@code PAYMENT_RESULT_UNKNOWN}을 올린다. 호출한 쪽은 이 경우를 실패로 확정하면 안 된다.
     */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 결과를 원장에 남기는 구간만 트랜잭션으로 묶는다.
    public PaymentCaptureResult capture(String sessionId, int expectedAmount) {
        Payment payment = requirePurchasePayment(sessionId);
        if (payment.isCaptured()) {
            return PaymentCaptureResult.captured(payment.getCapturedAt(), payment.getOutcomeCode());
        }
        requireAmountMatches(payment, expectedAmount);

        PaymentCaptureResult result = captureOrConfirm(sessionId, payment.getId());
        transactionTemplate.executeWithoutResult(status -> recordCapture(sessionId, result));
        return result;
    }

    /**
     * 승인을 보냈는지도 알 수 없는 세션의 실제 결과를 point3에 물어 가린다.
     *
     * <p>아직 결제창을 통과하지 않았거나 처리 중이면 비어 있다. 그 결과를 실패로 확정하면 안 된다.
     */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 가려낸 결과는 원장에도 남긴다.
    public Optional<PaymentCaptureResult> resolveSession(String sessionId) {
        PaymentSessionResponse session = point3PaymentClient.getSession(sessionId);
        if (!session.isCaptured() && !session.isFailed()) {
            log.info("결제 결과가 아직 정해지지 않았다 - sessionId={}, status={}", sessionId, session.status());
            return Optional.empty();
        }
        // 세션 조회는 승인 시각을 주지 않아 확인한 시각으로 남긴다.
        PaymentCaptureResult result =
                session.isCaptured()
                        ? PaymentCaptureResult.captured(LocalDateTime.now(), null)
                        : PaymentCaptureResult.rejected(null, session.status());
        transactionTemplate.executeWithoutResult(status -> recordCapture(sessionId, result));
        return Optional.of(result);
    }

    /**
     * 승인이 끝난 결제를 취소한다. 취소 가능 금액을 넘으면 거절한다.
     *
     * <p>응답을 받지 못한 요청은 처리 중으로 남는다. {@link #resumeRefund(Long)}로 이어서 처리해야 한다.
     */
    // 구매자가 직접 부르는 길은 없다. 재고가 모자라거나 결제가 확정되지 않아 서버가 되돌릴 때만 쓴다.
    public void refund(PaymentRefundCommand command) {
        int refundableBefore = requireRefundable(command);

        String idempotencyKey = newIdempotencyKey(command.orderId());
        int vat = vatOf(command.amount());
        Long refundId = saveRequestedRefund(command, vat, idempotencyKey);

        PaymentRefundResponse response =
                point3PaymentClient.createRefund(
                        command.sessionId(),
                        new PaymentRefundCreateRequest(
                                command.amount(), NO_TAX_FREE_AMOUNT, vat, command.reason()),
                        idempotencyKey);
        boolean fullyRefunded = refundableBefore == command.amount();
        transactionTemplate.executeWithoutResult(
                status -> recordRefund(refundId, response, command.sessionId(), fullyRefunded));
        log.info(
                "결제 취소 요청 - orderId={}, sessionId={}, amount={}, status={}",
                command.orderId(),
                command.sessionId(),
                command.amount(),
                response.status());
    }

    /** 처리 중으로 멈춘 취소를 이어서 처리하게 하고, 끝났으면 원장에 반영한다. */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다.
    public void resumeRefund(Long refundId) {
        Refund refund = requireRefund(refundId);
        if (refund.isCompleted()) {
            return;
        }
        PaymentRefundStatusResponse status =
                point3PaymentClient.resumeRefund(refund.getSessionId());
        if (status.isProcessing()) {
            log.info("결제 취소가 아직 처리 중이다 - refundId={}, status={}", refundId, status.status());
            return;
        }
        String sessionId = refund.getSessionId();
        boolean fullyRefunded = status.refundable() == 0;
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    requireRefund(refundId).complete(refund.getRefundId());
                    if (fullyRefunded) {
                        requirePayment(sessionId).refund();
                    }
                });
    }

    /** 취소를 걸 수 있는지 확인하고 지금 남은 취소 가능 금액을 돌려준다. */
    // 취소가 걸려 있는 동안에는 새 취소를 받지 않는다. 끝나지 않은 취소가 겹치면 point3가 409로 막는다.
    private int requireRefundable(PaymentRefundCommand command) {
        PaymentRefundStatusResponse status =
                point3PaymentClient.getRefundStatus(command.sessionId());
        if (status.isProcessing()) {
            throw new CustomException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }
        if (status.refundable() < command.amount()) {
            log.warn(
                    "취소 가능 금액을 넘은 취소 요청 - sessionId={}, refundable={}, requested={}",
                    command.sessionId(),
                    status.refundable(),
                    command.amount());
            throw new CustomException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED);
        }
        return status.refundable();
    }

    /** 승인 응답을 받지 못하면 세션을 조회해 실제 상태로 결과를 가린다. */
    private PaymentCaptureResult captureOrConfirm(String sessionId, Long paymentId) {
        try {
            PaymentCaptureResponse response = point3PaymentClient.capture(sessionId);
            if (response.isCaptured()) {
                return PaymentCaptureResult.captured(LocalDateTime.now(), response.outcomeCode());
            }
            log.warn(
                    "결제 승인 거절 - sessionId={}, status={}, outcome={}",
                    sessionId,
                    response.status(),
                    response.outcomeCode());
            return PaymentCaptureResult.rejected(response.outcomeCode(), response.failureMessage());
        } catch (CustomException e) {
            if (e.getErrorCode() != PaymentErrorCode.PAYMENT_RESULT_UNKNOWN) {
                throw e;
            }
            return confirmBySession(sessionId, paymentId, e);
        }
    }

    // 조회로도 가리지 못한 세션은 원장에 결과 미확인으로 남기고 예외를 그대로 올린다.
    private PaymentCaptureResult confirmBySession(
            String sessionId, Long paymentId, CustomException cause) {
        PaymentSessionResponse session = point3PaymentClient.getSession(sessionId);
        if (session.isCaptured()) {
            // 세션 조회는 승인 시각을 주지 않아 확인한 시각으로 남긴다.
            return PaymentCaptureResult.captured(LocalDateTime.now(), null);
        }
        if (session.isFailed()) {
            return PaymentCaptureResult.rejected(null, session.status());
        }
        log.error("결제 결과를 가리지 못했다 - sessionId={}, sessionStatus={}", sessionId, session.status());
        transactionTemplate.executeWithoutResult(
                status -> requirePaymentById(paymentId).markUnknown(session.status()));
        throw cause;
    }

    // 승인 결과는 세션 아이디로 다시 읽어 남긴다. 트랜잭션 밖에서 읽어 둔 Entity는 준영속이다.
    private void recordCapture(String sessionId, PaymentCaptureResult result) {
        Payment payment = requirePayment(sessionId);
        if (result.captured()) {
            payment.capture(result.outcomeCode(), result.capturedAt());
            return;
        }
        payment.fail(result.outcomeCode(), result.failureMessage());
    }

    // 남은 취소 가능 금액이 0이 되는 취소였으면 결제 원장도 취소로 옮긴다.
    private void recordRefund(
            Long refundId,
            PaymentRefundResponse response,
            String sessionId,
            boolean fullyRefunded) {
        Refund refund = requireRefund(refundId);
        if (!response.isCompleted()) {
            refund.keepProcessing(response.id());
            return;
        }
        refund.complete(response.id());
        if (fullyRefunded) {
            requirePayment(sessionId).refund();
        }
    }

    /** 요청을 보내기 전에 남긴다. 응답을 받지 못해도 무엇을 걸었는지 남아 있어야 결과를 다시 확인할 수 있다. */
    private Long saveRequestedRefund(PaymentRefundCommand command, int vat, String idempotencyKey) {
        return transactionTemplate.execute(
                status ->
                        refundRepository
                                .save(
                                        Refund.request(
                                                command, NO_TAX_FREE_AMOUNT, vat, idempotencyKey))
                                .getId());
    }

    // 우리가 만든 세션 금액과 승인하려는 금액이 다르면 주문과 결제가 어긋난 것이다.
    private void requireAmountMatches(Payment payment, int expectedAmount) {
        if (payment.getAmount() == null || payment.getAmount() != expectedAmount) {
            log.error(
                    "주문 금액과 결제 세션 금액이 다르다 - sessionId={}, sessionAmount={}, orderAmount={}",
                    payment.getSessionId(),
                    payment.getAmount(),
                    expectedAmount);
            throw new CustomException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private Payment requirePurchasePayment(String sessionId) {
        Payment payment = requirePayment(sessionId);
        if (payment.getPurpose() != PaymentPurpose.PRODUCT_PURCHASE) {
            throw new CustomException(PaymentErrorCode.PAYMENT_SESSION_NOT_FOUND);
        }
        return payment;
    }

    private Payment requirePayment(String sessionId) {
        return paymentRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_SESSION_NOT_FOUND));
    }

    private Payment requirePaymentById(Long paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_SESSION_NOT_FOUND));
    }

    private Refund requireRefund(Long refundId) {
        return refundRepository
                .findById(refundId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_REFUND_FAILED));
    }

    // 면세 상품이 없어 전액을 과세로 본다. point3가 세션을 만들 때 쓰는 식과 같게 계산한다.
    private int vatOf(int amount) {
        return (int) Math.round(amount / VAT_DIVISOR);
    }

    // 같은 취소를 두 번 접수시키지 않으려고 요청마다 새로 만든다. 재시도는 같은 키로 나간다.
    private String newIdempotencyKey(Long orderId) {
        return "order-" + orderId + "-" + UUID.randomUUID();
    }
}
