package com.toasty.domain.follow.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매자가 판매자를 팔로우한 관계 Entity. 한 구매자가 여러 판매자를 팔로우할 수 있다.
 *
 * <p>방향은 구매자 -> 판매자 한쪽뿐이라, 두 컬럼이 각각 다른 테이블을 참조해 방향을 고정한다.
 */
@Entity
@Getter
@Table(name = "follows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 팔로우하는 구매자
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // 팔로우 대상인 판매자
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    private Follow(Long customerId, Long sellerId) {
        this.customerId = customerId;
        this.sellerId = sellerId;
    }

    /** 구매자가 판매자를 팔로우할 때 만들어진다. */
    public static Follow create(Long customerId, Long sellerId) {
        return new Follow(customerId, sellerId);
    }
}
