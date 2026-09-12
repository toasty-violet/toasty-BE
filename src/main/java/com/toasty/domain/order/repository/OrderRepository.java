package com.toasty.domain.order.repository;

import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 셀러 주문탭 목록. 최신순이라 id 내림차순이고, 커서보다 작은 id부터 읽는다. */
    List<Order> findBySellerIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long sellerId, Collection<OrderStatus> statuses, Long cursor, Pageable pageable);

    /** 주문탭 상태 칩에 붙는 건수. */
    @Query(
            "select o.status as status, count(o) as orderCount from Order o"
                    + " where o.sellerId = :sellerId group by o.status")
    List<SellerOrderCount> countBySellerIdGroupByStatus(@Param("sellerId") Long sellerId);
}
