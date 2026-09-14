package com.toasty.domain.order.repository;

import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 운송장 등록. 동시에 두 번 눌려도 한 요청만 발송완료로 넘기도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findForUpdateById(@Param("orderId") Long orderId);

    /** 셀러 주문탭 목록. 최신순이라 id 내림차순이고, 커서보다 작은 id부터 읽는다. */
    List<Order> findBySellerIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long sellerId, Collection<OrderStatus> statuses, Long cursor, Pageable pageable);

    /** 구매자 주문내역 목록. 최신순이라 id 내림차순이고, 커서보다 작은 id부터 읽는다. */
    List<Order> findByCustomerIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long customerId, Collection<OrderStatus> statuses, Long cursor, Pageable pageable);

    /** 셀러 주문탭 상태 칩에 붙는 건수. */
    @Query(
            "select o.status as status, count(o) as orderCount from Order o"
                    + " where o.sellerId = :sellerId group by o.status")
    List<OrderStatusCount> countBySellerIdGroupByStatus(@Param("sellerId") Long sellerId);

    /** 구매자 주문내역 상태 칩에 붙는 건수. */
    @Query(
            "select o.status as status, count(o) as orderCount from Order o"
                    + " where o.customerId = :customerId group by o.status")
    List<OrderStatusCount> countByCustomerIdGroupByStatus(@Param("customerId") Long customerId);
}
