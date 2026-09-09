package com.toasty.domain.product.controller.dto.request;

import com.toasty.domain.product.entity.ProductImageUploadCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductImageUploadUrlRequest(
        @Schema(description = "올릴 사진 목록")
                @NotEmpty(message = "올릴 사진 정보는 필수입니다.") @Size(max = 20, message = "사진은 한 번에 20장까지 올릴 수 있습니다.") List<@Valid @NotNull File> files) {

    public ProductImageUploadCommand toCommand(Long sellerId) {
        return new ProductImageUploadCommand(
                sellerId,
                files.stream()
                        .map(
                                f ->
                                        new ProductImageUploadCommand.File(
                                                f.contentType(), f.contentLength()))
                        .toList());
    }

    public record File(
            @Schema(description = "사진 형식", example = "image/jpeg")
                    @NotBlank(message = "사진 형식은 필수입니다.") @Pattern(
                            regexp = "image/(jpeg|png|webp)",
                            message = "사진은 jpeg, png, webp만 올릴 수 있습니다.")
                    String contentType,
            @Schema(description = "사진 크기(바이트)", example = "812304")
                    @Positive(message = "사진 크기는 1바이트 이상이어야 합니다.") @Max(value = 10_485_760, message = "사진은 10MB를 넘을 수 없습니다.") long contentLength) {}
}
