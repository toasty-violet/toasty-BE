package com.toasty.domain.sample.service;

import com.toasty.domain.sample.controller.dto.response.SampleResponse;
import com.toasty.domain.sample.entity.Sample;
import com.toasty.domain.sample.entity.SampleCreateCommand;
import com.toasty.domain.sample.exception.SampleErrorCode;
import com.toasty.domain.sample.repository.SampleRepository;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도메인 구조 예시용 Service. */
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;

    @Transactional
    public SampleResponse create(SampleCreateCommand command) {
        Sample sample = sampleRepository.save(Sample.create(command));
        return SampleResponse.from(sample);
    }

    @Transactional(readOnly = true)
    public SampleResponse getById(Long id) {
        Sample sample =
                sampleRepository
                        .findById(id)
                        .orElseThrow(() -> new CustomException(SampleErrorCode.SAMPLE_NOT_FOUND));
        return SampleResponse.from(sample);
    }
}
