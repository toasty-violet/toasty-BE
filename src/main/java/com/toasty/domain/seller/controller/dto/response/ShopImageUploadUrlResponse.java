package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShopImageUploadUrlResponse(
        @Schema(
                        description = "버킷에 저장될 위치. 업로드에 성공하면 셀러 온보딩 제출에 이 값을 넣는다",
                        example = "sellers/images/7/2026/09/05/9f3c1a2e-....jpg")
                String objectKey,
        @Schema(description = "이 주소로 사진 본문만 PUT 한다") String uploadUrl,
        @Schema(description = "주소 유효 시간(초)", example = "300") int expiresIn) {}
