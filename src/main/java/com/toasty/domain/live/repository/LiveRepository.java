package com.toasty.domain.live.repository;

import com.toasty.domain.live.entity.Live;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveRepository extends JpaRepository<Live, Long> {}
