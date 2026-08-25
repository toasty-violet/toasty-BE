package com.toasty.domain.live.controller.dto.request;

import com.toasty.domain.live.entity.LiveCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveCreateRequest(
        @Schema(description = "라이브 제목", example = "빈티지 여름옷 라이브")
                @NotBlank(message = "제목은 필수입니다.") @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
        @Schema(description = "라이브 설명", example = "여름 상품을 소개합니다")
                @Size(max = 1000, message = "설명은 1000자를 넘을 수 없습니다.") String description) {

    public LiveCreateCommand toCommand(Long sellerId) {
        return new LiveCreateCommand(sellerId, title, description);
    }
}
