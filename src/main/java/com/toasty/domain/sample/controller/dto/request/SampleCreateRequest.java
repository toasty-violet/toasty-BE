package com.toasty.domain.sample.controller.dto.request;

import com.toasty.domain.sample.entity.SampleCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 생성 요청 본문. 검증 메시지는 응답의 {@code error.fields[].message}로 그대로 노출된다. */
public record SampleCreateRequest(
        @Schema(description = "제목", example = "첫 샘플")
                @NotBlank(message = "제목은 필수입니다.") @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
        @Schema(description = "내용", example = "구조 확인용")
                @Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다.") String content) {

    public SampleCreateCommand toCommand() {
        return new SampleCreateCommand(title, content);
    }
}
