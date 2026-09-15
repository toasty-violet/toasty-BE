package com.toasty.domain.order.repository;

import com.toasty.domain.order.entity.LiveSalesStat;
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

    /** 라이브 한 건에서 나온 판매 집계. 판매 금액은 배송비를 뺀 상품 금액이다. */
    // 셀러 번호를 함께 걸어, 다른 셀러의 상품을 이 라이브 번호로 주문해도 집계에 섞이지 않는다.
    @Query(
            "select new com.toasty.domain.order.entity.LiveSalesStat("
                    + " count(o), coalesce(sum(o.productPrice * o.quantity), 0),"
                    + " coalesce(sum(o.quantity), 0))"
                    + " from Order o"
                    + " where o.liveId = :liveId and o.sellerId = :sellerId"
                    + " and o.status in :statuses")
    LiveSalesStat findLiveSalesStat(
            @Param("liveId") Long liveId,
            @Param("sellerId") Long sellerId,
            @Param("statuses") Collection<OrderStatus> statuses);

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
