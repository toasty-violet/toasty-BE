package com.toasty.domain.payment.repository;

import com.toasty.domain.payment.entity.Refund;
import com.toasty.domain.payment.entity.RefundStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderId(Long orderId);

    /** 끝나지 않은 취소. 재개 호출로 이어서 처리해야 하는 건들이다. */
    List<Refund> findByStatus(RefundStatus status);
}
