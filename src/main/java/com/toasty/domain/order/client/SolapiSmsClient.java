package com.toasty.domain.order.client;

import com.toasty.domain.order.client.dto.SmsSendRequest;
import com.toasty.domain.order.client.dto.SmsSendResponse;
import com.toasty.global.config.SolapiProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Solapi로 문자를 보낸다. 길이에 따라 SMS·LMS는 Solapi가 고른다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolapiSmsClient {

    private static final String SEND_PATH = "/messages/v4/send";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 뒤에서 도는 스레드라 응답이 끊겨도 영영 붙잡혀 있지 않게 한다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final SolapiProperties solapiProperties;
    private final RestClient restClient =
            RestClient.builder().requestFactory(timeoutRequestFactory()).build();

    /** 받는 번호로 문자 한 건을 보낸다. 실패하면 예외를 그대로 던진다. */
    public void send(String to, String text) {
        if (!solapiProperties.isConfigured()) {
            log.info("Solapi 키가 없어 문자를 보내지 않는다 - text={}", text);
            return;
        }
        SmsSendResponse response =
                restClient
                        .post()
                        .uri(solapiProperties.baseUrl() + SEND_PATH)
                        .header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                SmsSendRequest.of(
                                        digitsOnly(to), solapiProperties.senderNumber(), text))
                        .retrieve()
                        .body(SmsSendResponse.class);
        if (response != null) {
            log.info(
                    "문자 발송 접수 - messageId={}, status={} {}",
                    response.messageId(),
                    response.statusCode(),
                    response.statusMessage());
        }
    }

    private String authorization() {
        String date = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString();
        String salt = UUID.randomUUID().toString().replace("-", "");
        return authorization(solapiProperties.apiKey(), solapiProperties.apiSecret(), date, salt);
    }

    // Solapi 공식 SDK(net.nurigo:sdk)의 Authenticator와 같은 규칙이다. 서명 대상은 date + salt다.
    static String authorization(String apiKey, String apiSecret, String date, String salt) {
        return "HMAC-SHA256 Apikey=%s, Date=%s, salt=%s, signature=%s"
                .formatted(apiKey, date, salt, sign(apiSecret, date + salt));
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다.", e);
        }
    }

    private static JdkClientHttpRequestFactory timeoutRequestFactory() {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    static String digitsOnly(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
}
