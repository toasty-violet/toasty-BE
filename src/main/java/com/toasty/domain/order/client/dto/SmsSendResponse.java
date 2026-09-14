package com.toasty.domain.order.client.dto;

/** Solapi 단건 발송 응답 중 발송 추적에 쓰는 값. */
public record SmsSendResponse(String messageId, String statusCode, String statusMessage) {}
