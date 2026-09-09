package com.toasty.domain.follow.repository;

import com.toasty.domain.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    void deleteAllByCustomerId(Long customerId);

    void deleteAllBySellerId(Long sellerId);
}
