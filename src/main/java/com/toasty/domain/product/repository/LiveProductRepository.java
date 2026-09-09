package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.LiveProduct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveProductRepository extends JpaRepository<LiveProduct, Long> {

    Optional<LiveProduct> findByLiveIdAndProductId(Long liveId, Long productId);

    /** 편성은 항상 노출 순서로 읽는다. 순서가 필요 없는 곳도 편성이 최대 50건이라 정렬 비용이 무의미하다. */
    List<LiveProduct> findByLiveIdOrderByDisplayOrder(Long liveId);

    @Query(
            "select lp.liveId as liveId, count(lp) as productCount from LiveProduct lp"
                    + " where lp.liveId in :liveIds group by lp.liveId")
    List<LiveProductCount> countByLiveIdIn(@Param("liveIds") Collection<Long> liveIds);

    /** 넘긴 상품들 중 이 라이브 말고 다른 라이브에도 편성돼 있는 것만 돌려준다. */
    @Query(
            "select distinct lp.productId from LiveProduct lp"
                    + " where lp.productId in :productIds and lp.liveId <> :liveId")
    List<Long> findProductIdsScheduledInOtherLives(
            @Param("productIds") Collection<Long> productIds, @Param("liveId") Long liveId);
}
