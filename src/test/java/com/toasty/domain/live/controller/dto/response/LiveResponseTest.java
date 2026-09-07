package com.toasty.domain.live.controller.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 스트림 키는 방송 권한 그 자체다. 로그·공용 응답으로 새지 않는지 지킨다. */
class LiveResponseTest {

    @Test
    @DisplayName("송출정보를 로그에 찍어도 streamKey는 가려진다")
    void streamKey는_toString에서_가려진다() {
        BroadcastCredentialResponse response =
                BroadcastCredentialResponse.from(
                        new BroadcastCredential("ingest.example.com", "sk-super-secret"));

        assertThat(response.toString())
                .doesNotContain("sk-super-secret")
                .contains("streamKey=***")
                .contains("ingest.example.com");
    }

    @Test
    @DisplayName("라이브탭 응답은 값이 없어도 키를 남긴다")
    void 라이브탭_응답은_null_키를_남긴다() throws Exception {
        String json =
                objectMapperWithGlobalSetting()
                        .writeValueAsString(SellerLiveTabResponse.of(null, java.util.List.of()));

        assertThat(json)
                .contains("\"latestStat\":null")
                .contains("\"broadcasting\":null")
                .contains("\"scheduled\":[]");
    }

    // 전역 설정이 non_null이라, 그대로 두면 화면이 빈 상태를 그릴 근거가 응답에서 사라진다.
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapperWithGlobalSetting() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }

    @Test
    @DisplayName("라이브탭 응답에도 송출정보 필드가 없다")
    void 라이브탭_응답에는_송출정보가_없다() {
        assertThat(SellerLiveTabResponse.Broadcasting.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("streamKey", "ingestEndpoint");
    }

    @Test
    @DisplayName("공용 상세 응답에는 송출정보 필드가 아예 없다")
    void 상세_응답에는_송출정보_필드가_없다() {
        assertThat(LiveDetailResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("streamKey", "streamKeyArn", "ingestEndpoint");
    }

    @Test
    @DisplayName("생성 응답에도 송출정보 필드가 없다")
    void 생성_응답에는_송출정보가_없다() {
        assertThat(LiveCreateResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("streamKey", "ingestEndpoint", "broadcastCredential");
    }

    @Test
    @DisplayName("상세 응답을 로그에 찍어도 셀러 식별자 외에 민감정보가 없다")
    void 상세_응답에는_비밀값이_없다() {
        LiveDetailResponse response =
                new LiveDetailResponse(
                        1L,
                        "public-id",
                        7L,
                        "제목",
                        "설명",
                        java.time.LocalDateTime.now().plusDays(1),
                        com.toasty.domain.live.entity.LiveStatus.READY,
                        "https://playback.example.com/abc.m3u8",
                        null,
                        null,
                        null);

        assertThat(response.toString()).doesNotContain("sk-", "streamKey", "ingest");
    }
}
