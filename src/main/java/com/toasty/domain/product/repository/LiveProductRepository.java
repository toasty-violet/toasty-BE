package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.LiveProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveProductRepository extends JpaRepository<LiveProduct, Long> {

    List<LiveProduct> findByLiveId(Long liveId);

    boolean existsByProductIdAndLiveIdNot(Long productId, Long liveId);
}
