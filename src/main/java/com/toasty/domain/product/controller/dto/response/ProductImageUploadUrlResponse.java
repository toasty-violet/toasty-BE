package com.toasty.domain.product.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ProductImageUploadUrlResponse(List<Upload> uploads) {

    public record Upload(
            @Schema(
                            description = "라이브 생성 요청에 그대로 넣을 값",
                            example = "products/pending/2026/09/02/9f3c1a2e-....jpg")
                    String objectKey,
            @Schema(description = "이 주소로 사진 본문만 PUT 한다") String uploadUrl,
            @Schema(description = "주소 유효 시간(초)", example = "300") int expiresIn) {}
}
