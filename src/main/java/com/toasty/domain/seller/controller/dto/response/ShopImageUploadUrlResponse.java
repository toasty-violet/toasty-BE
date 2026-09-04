package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShopImageUploadUrlResponse(
        @Schema(
                        description = "버킷에 저장될 위치",
                        example = "sellers/images/7/2026/09/05/9f3c1a2e-....jpg")
                String objectKey,
        @Schema(description = "이 주소로 사진 본문만 PUT 한다") String uploadUrl,
        @Schema(
                        description = "업로드에 성공하면 사진을 읽을 수 있는 주소. 셀러 온보딩 제출에 그대로 넣는다",
                        example =
                                "https://cdn.example.com/sellers/images/7/2026/09/05/9f3c1a2e-....jpg")
                String imageUrl,
        @Schema(description = "주소 유효 시간(초)", example = "300") int expiresIn) {}
