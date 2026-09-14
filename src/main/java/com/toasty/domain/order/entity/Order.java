package com.toasty.domain.order.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 Entity. 장바구니가 없어 주문 하나에 상품도 하나다.
 *
 * <p>상품과 배송지는 나중에 지워지거나 바뀌므로 주문 시점 값을 복사해 둔다.
 */
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 화면에 보여주는 주문번호
    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(name = "product_price", nullable = false)
    private int productPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "shipping_fee", nullable = false)
    private int shippingFee;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(length = 30)
    private String courier;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    public boolean isOwnedBySeller(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public boolean isOwnedByCustomer(Long customerId) {
        return this.customerId.equals(customerId);
    }
}
