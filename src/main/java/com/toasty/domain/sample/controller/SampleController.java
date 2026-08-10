package com.toasty.domain.sample.controller;

import com.toasty.domain.sample.controller.dto.request.SampleCreateRequest;
import com.toasty.domain.sample.controller.dto.response.SampleResponse;
import com.toasty.domain.sample.service.SampleService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sample", description = "도메인 구조 예시 API — 실제 기능을 시작하면 삭제한다")
@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @Operation(summary = "샘플 생성")
    @PostMapping
    public ApiResponse<SampleResponse> create(@Valid @RequestBody SampleCreateRequest request) {
        return ApiResponse.ok(sampleService.create(request.toCommand()));
    }

    @Operation(summary = "샘플 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<SampleResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(sampleService.getById(id));
    }
}
