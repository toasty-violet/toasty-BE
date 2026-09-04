package com.toasty.domain.seller.controller.dto.request;

import com.toasty.domain.seller.entity.ShopImageUploadCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ShopImageUploadUrlRequest(
        @Schema(description = "사진 형식", example = "image/jpeg")
                @NotBlank(message = "사진 형식은 필수입니다.") @Pattern(
                        regexp = "image/(jpeg|png|webp)",
                        message = "사진은 jpeg, png, webp만 올릴 수 있습니다.")
                String contentType,
        @Schema(description = "사진 크기(바이트)", example = "812304")
                @Positive(message = "사진 크기는 1바이트 이상이어야 합니다.") @Max(value = 10_485_760, message = "사진은 10MB를 넘을 수 없습니다.") long contentLength) {

    public ShopImageUploadCommand toCommand(Long userId) {
        return new ShopImageUploadCommand(userId, contentType, contentLength);
    }
}
