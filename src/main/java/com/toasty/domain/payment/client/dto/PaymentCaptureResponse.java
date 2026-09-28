package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/** point3 결제 승인 응답. 상태값은 소문자로 내려온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCaptureResponse(
        String id, String status, Outcome outcome, LocalDate committedAt) {

    private static final String CAPTURED = "captured";
    private static final String EXPIRED = "expired";

    /** 승인 결과를 기계가 읽는 코드와 사람이 읽는 문구로 함께 알려준다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Outcome(String type, String code, String message) {}

    /** 승인이 끝나 결제금액이 빠져나간 상태인지 판단한다. */
    public boolean isCaptured() {
        return CAPTURED.equalsIgnoreCase(status);
    }

    /** 결제를 커밋한 다음 날 00:00(KST)을 넘겨 더는 승인할 수 없는 세션인지 판단한다. */
    public boolean isExpired() {
        return EXPIRED.equalsIgnoreCase(status);
    }

    /** 실패 사유로 남길 문구. point3가 문구를 주지 않으면 상태값으로 대신한다. */
    public String failureMessage() {
        return outcome == null || outcome.message() == null ? status : outcome.message();
    }

    public String outcomeCode() {
        return outcome == null ? null : outcome.code();
    }
}
