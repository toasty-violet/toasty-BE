package com.toasty.domain.order.entity;

import com.toasty.domain.customer.entity.ShippingDestination;
import com.toasty.domain.product.entity.ReservedProduct;
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
 *
 * <p>주문하기를 누른 시점에 결제 대기로 만들고, 결제 승인 결과로 배송대기나 결제실패로 옮긴다.
 */
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    // 실패 사유는 화면에 그대로 뿌리지 않고 주문에만 남기므로 컬럼 길이에서 자른다.
    private static final int FAILURE_REASON_MAX_LENGTH = 255;

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

    // 선점한 재고를 되돌릴 상품. 상품이 지워질 수 있어 외래키는 걸지 않는다
    @Column(name = "product_id")
    private Long productId;

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

    // point3 결제 세션. 승인·취소가 모두 이 값으로 이뤄진다
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "payment_failure_reason", length = 255)
    private String paymentFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Courier courier;

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

    private Order(
            String orderNumber,
            OrderCreateCommand command,
            ReservedProduct product,
            ShippingDestination destination,
            int shippingFee) {
        this.orderNumber = orderNumber;
        this.customerId = command.customerId();
        this.sellerId = product.sellerId();
        this.productId = product.productId();
        this.productName = product.name();
        this.productImageUrl = product.imageUrl();
        this.productPrice = product.price();
        this.quantity = command.quantity();
        this.shippingFee = shippingFee;
        this.totalAmount = product.price() * command.quantity() + shippingFee;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.receiverName = destination.receiverName();
        this.receiverPhone = destination.receiverPhone();
        this.postalCode = destination.postalCode();
        this.address = destination.address();
        this.detailAddress = destination.detailAddress();
    }

    /** 주문하기를 누른 시점에 만든다. 재고는 이미 선점했고 결제는 아직 끝나지 않았다. */
    public static Order createPending(
            String orderNumber,
            OrderCreateCommand command,
            ReservedProduct product,
            ShippingDestination destination,
            int shippingFee) {
        return new Order(orderNumber, command, product, destination, shippingFee);
    }

    public boolean isOwnedBySeller(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public boolean isOwnedByCustomer(Long customerId) {
        return this.customerId.equals(customerId);
    }

    /** 화면에 그대로 찍는 택배사 이름. 등록 전이면 null이다. */
    public String courierName() {
        return courier == null ? null : courier.displayName();
    }

    public boolean isShipped() {
        return status == OrderStatus.SHIPPED;
    }

    public boolean isPaymentPending() {
        return status == OrderStatus.PAYMENT_PENDING;
    }

    /** 결제가 끝나 판매자가 보내야 하는 주문인지 판단한다. */
    public boolean isPaid() {
        return status == OrderStatus.SHIPPING_PENDING || status == OrderStatus.SHIPPED;
    }

    /** 결제창을 열 세션을 붙인다. 세션을 만든 직후에 한 번만 붙인다. */
    public void linkSession(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 결제 승인이 끝나 배송대기로 넘긴다. */
    public void pay(LocalDateTime paidAt) {
        this.status = OrderStatus.SHIPPING_PENDING;
        this.paidAt = paidAt;
        this.paymentFailureReason = null;
    }

    /** 결제가 승인되지 않아 결제실패로 넘긴다. */
    public void failPayment(String reason) {
        this.status = OrderStatus.PAYMENT_FAILED;
        this.paymentFailureReason = shorten(reason);
    }

    /** 결제까지 끝난 주문을 되돌려 취소로 넘긴다. */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.paymentFailureReason = shorten(reason);
    }

    /** 운송장을 달아 발송완료로 넘긴다. */
    public void ship(Courier courier, String trackingNumber) {
        this.courier = courier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = LocalDateTime.now();
        this.status = OrderStatus.SHIPPED;
    }

    private String shorten(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= FAILURE_REASON_MAX_LENGTH
                ? reason
                : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
