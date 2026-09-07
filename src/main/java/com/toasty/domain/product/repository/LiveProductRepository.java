package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.LiveProduct;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveProductRepository extends JpaRepository<LiveProduct, Long> {

    List<LiveProduct> findByLiveId(Long liveId);

    List<LiveProduct> findByLiveIdOrderByDisplayOrder(Long liveId);

    boolean existsByProductIdAndLiveIdNot(Long productId, Long liveId);

    @Query(
            "select lp.liveId as liveId, count(lp) as productCount from LiveProduct lp"
                    + " where lp.liveId in :liveIds group by lp.liveId")
    List<LiveProductCount> countByLiveIdIn(@Param("liveIds") Collection<Long> liveIds);
}
