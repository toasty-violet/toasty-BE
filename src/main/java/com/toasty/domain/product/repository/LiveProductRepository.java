package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.LiveProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveProductRepository extends JpaRepository<LiveProduct, Long> {}
