package com.toasty.domain.order.client.dto;

/** Solapi 단건 발송 요청 본문. */
public record SmsSendRequest(Message message) {

    public record Message(String to, String from, String text) {}

    public static SmsSendRequest of(String to, String from, String text) {
        return new SmsSendRequest(new Message(to, from, text));
    }
}
