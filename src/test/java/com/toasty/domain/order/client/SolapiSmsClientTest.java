package com.toasty.domain.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.toasty.global.config.SolapiProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Solapi 문자 클라이언트")
class SolapiSmsClientTest {

    @Test
    @DisplayName("인증 헤더는 date+salt를 시크릿으로 HMAC-SHA256 서명한 hex 값을 담는다")
    void 인증_헤더를_만든다() throws Exception {
        String date = "2026-09-15T01:23:45.123+09:00";
        String salt = "0123456789abcdef0123456789abcdef";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected =
                HexFormat.of()
                        .formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));

        String header = SolapiSmsClient.authorization("KEY", "secret", date, salt);

        assertThat(header)
                .isEqualTo(
                        "HMAC-SHA256 Apikey=KEY, Date="
                                + date
                                + ", salt="
                                + salt
                                + ", signature="
                                + expected);
        assertThat(expected).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("키가 없으면 요청을 보내지 않고 조용히 끝난다")
    void 키가_없으면_보내지_않는다() {
        SolapiSmsClient client =
                new SolapiSmsClient(new SolapiProperties("http://127.0.0.1:9", "", "", ""));

        assertThatCode(() -> client.send("010-2345-6789", "본문")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("받는 번호는 하이픈과 공백을 빼고 숫자만 보낸다")
    void 번호는_숫자만_보낸다() {
        assertThat(SolapiSmsClient.digitsOnly("010-2345 6789")).isEqualTo("01023456789");
    }
}
