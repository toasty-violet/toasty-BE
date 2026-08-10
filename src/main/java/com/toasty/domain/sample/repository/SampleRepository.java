package com.toasty.domain.sample.repository;

import com.toasty.domain.sample.entity.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRepository extends JpaRepository<Sample, Long> {}
