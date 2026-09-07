package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.LiveProduct;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveProductRepository extends JpaRepository<LiveProduct, Long> {

    List<LiveProduct> findByLiveId(Long liveId);

    /** 넘긴 상품들 중 이 라이브 말고 다른 라이브에도 편성돼 있는 것만 돌려준다. */
    @Query(
            "select distinct lp.productId from LiveProduct lp"
                    + " where lp.productId in :productIds and lp.liveId <> :liveId")
    List<Long> findProductIdsScheduledInOtherLives(
            @Param("productIds") Collection<Long> productIds, @Param("liveId") Long liveId);
}
