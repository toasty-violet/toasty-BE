package com.toasty.domain.sample.controller.dto.response;

import com.toasty.domain.sample.entity.Sample;
import java.time.LocalDateTime;

/** 응답 본문. {@code ApiResponse.data}에 담긴다. */
public record SampleResponse(Long id, String title, String content, LocalDateTime createdAt) {

    public static SampleResponse from(Sample sample) {
        return new SampleResponse(
                sample.getId(), sample.getTitle(), sample.getContent(), sample.getCreatedAt());
    }
}
