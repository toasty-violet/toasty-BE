package com.toasty.domain.follow.repository;

import com.toasty.domain.follow.entity.Follow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    long countBySellerId(Long sellerId);

    void deleteAllByCustomerId(Long customerId);

    void deleteAllBySellerId(Long sellerId);

    boolean existsByCustomerIdAndSellerId(Long customerId, Long sellerId);

    void deleteByCustomerIdAndSellerId(Long customerId, Long sellerId);

    /** 인기 스토어 목록. 팔로워가 많은 순으로 준다. */
    // 팔로워 수가 같아도 순서가 흔들리지 않게 seller_id를 뒤에 둔다.
    // 팔로워가 한 명도 없는 스토어는 이 테이블에 행이 없어 담기지 않는다.
    @Query(
            "select f.sellerId as sellerId, count(f) as followerCount from Follow f"
                    + " group by f.sellerId"
                    + " order by count(f) desc, f.sellerId desc")
    List<FollowerCount> findSellerIdsOrderByFollowerCountDesc(Pageable pageable);

    /** 목록 화면이 스토어마다 조회하지 않도록 요청한 유저가 팔로우 중인 스토어를 한 번에 가려낸다. */
    @Query(
            "select f.sellerId from Follow f"
                    + " where f.customerId = :customerId and f.sellerId in :sellerIds")
    List<Long> findFollowedSellerIds(
            @Param("customerId") Long customerId, @Param("sellerIds") Collection<Long> sellerIds);
}
