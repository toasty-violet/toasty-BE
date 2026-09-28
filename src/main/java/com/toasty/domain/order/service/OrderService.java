package com.toasty.domain.order.service;

import com.toasty.domain.customer.service.CustomerService;
import com.toasty.domain.order.controller.dto.response.CourierResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrderResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrdersResponse;
import com.toasty.domain.order.controller.dto.response.OrderCountsResponse;
import com.toasty.domain.order.controller.dto.response.OrderCreateResponse;
import com.toasty.domain.order.controller.dto.response.OrderPaymentResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrderResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrdersResponse;
import com.toasty.domain.order.entity.Courier;
import com.toasty.domain.order.entity.CustomerOrderPageCommand;
import com.toasty.domain.order.entity.LiveSalesStat;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderCreateCommand;
import com.toasty.domain.order.entity.OrderShipmentCommand;
import com.toasty.domain.order.entity.OrderShippedEvent;
import com.toasty.domain.order.entity.OrderStatus;
import com.toasty.domain.order.entity.SellerOrderPageCommand;
import com.toasty.domain.order.entity.SellerSalesStat;
import com.toasty.domain.order.exception.OrderErrorCode;
import com.toasty.domain.order.repository.OrderRepository;
import com.toasty.domain.order.repository.OrderStatusCount;
import com.toasty.domain.payment.entity.PaymentCaptureResult;
import com.toasty.domain.payment.entity.PaymentRefundCommand;
import com.toasty.domain.payment.entity.PurchaseSessionCommand;
import com.toasty.domain.payment.exception.PaymentErrorCode;
import com.toasty.domain.payment.service.PaymentService;
import com.toasty.domain.product.entity.ReservedProduct;
import com.toasty.domain.product.service.ProductService;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 구매자가 주문하고 결제를 끝내면, 셀러가 주문을 훑고 운송장을 등록해 배송 시작을 알린다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // 주문탭이 한 번에 당겨오는 개수. 무한스크롤이다.
    private static final int ORDER_PAGE_SIZE = 20;

    // 첫 페이지는 커서가 없다. id는 양수라 최댓값을 넣으면 맨 앞부터 읽는다.
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;

    // 화면에 보여주는 주문번호. 날짜 뒤에 붙는 값으로 같은 날 주문끼리 갈린다.
    private static final DateTimeFormatter ORDER_NUMBER_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NUMBER_SUFFIX_LENGTH = 8;

    // 판매 집계에 담는 주문. 결제가 끝난 주문만 세고, 결제실패와 취소는 뺀다.
    private static final List<OrderStatus> PAID_STATUSES =
            List.of(OrderStatus.SHIPPING_PENDING, OrderStatus.SHIPPED);

    private static final String SESSION_CREATE_FAILED_REASON = "결제 세션을 만들지 못했습니다.";
    private static final String CHECKOUT_HOLD_EXPIRED_REASON = "결제를 끝내지 않아 선점을 풀었습니다.";
    private static final String REVERTED_ORDER_REFUND_REASON = "되돌린 주문에 결제가 승인돼 취소합니다.";

    private final OrderRepository orderRepository;
    private final SellerService sellerService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** 셀러 주문탭 한 묶음을 채운다. */
    // 건수는 스크롤 중에 바뀌지 않아 첫 요청에서만 센다.
    @Transactional(readOnly = true)
    public SellerOrdersResponse findSellerOrders(SellerOrderPageCommand command) {
        CursorPage page =
                toCursorPage(
                        orderRepository.findBySellerIdAndStatusInAndIdLessThanOrderByIdDesc(
                                command.sellerId(),
                                command.filter().statuses(),
                                cursorOf(command.cursor()),
                                oneMoreThanPage()));
        return new SellerOrdersResponse(
                command.cursor() == null ? countSellerOrders(command.sellerId()) : null,
                page.orders().stream().map(SellerOrderResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }

    /** 다른 도메인이 라이브 한 건의 판매 집계를 읽는다. */
    @Transactional(readOnly = true)
    public LiveSalesStat findLiveSalesStat(Long liveId, Long sellerId) {
        return orderRepository.findLiveSalesStat(liveId, sellerId, PAID_STATUSES);
    }

    /** 다른 도메인이 스토어탭 판매 내역 요약을 읽는다. */
    @Transactional(readOnly = true)
    public SellerSalesStat findSellerSalesStat(Long sellerId) {
        return orderRepository.findSellerSalesStat(sellerId, PAID_STATUSES);
    }

    /** 구매자 주문내역 한 묶음을 채운다. */
    // 스토어 이름은 주문마다 읽지 않고 한 번에 모아 읽는다.
    @Transactional(readOnly = true)
    public CustomerOrdersResponse findCustomerOrders(CustomerOrderPageCommand command) {
        CursorPage page =
                toCursorPage(
                        orderRepository.findByCustomerIdAndStatusInAndIdLessThanOrderByIdDesc(
                                command.customerId(),
                                command.filter().statuses(),
                                cursorOf(command.cursor()),
                                oneMoreThanPage()));
        Map<Long, String> shopNames = findShopNames(page.orders());
        return new CustomerOrdersResponse(
                command.cursor() == null ? countCustomerOrders(command.customerId()) : null,
                page.orders().stream()
                        .map(
                                order ->
                                        CustomerOrderResponse.of(
                                                order, shopNames.get(order.getSellerId())))
                        .toList(),
                page.nextCursor(),
                page.hasNext());
    }

    /** 구매자 주문 상세 화면을 채운다. */
    @Transactional(readOnly = true)
    public CustomerOrderDetailResponse findCustomerOrder(Long orderId, Long customerId) {
        Order order =
                orderRepository
                        .findById(orderId)
                        .filter(found -> found.isOwnedByCustomer(customerId))
                        .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
        return CustomerOrderDetailResponse.of(
                order, sellerService.findShopProfile(order.getSellerId()).shopName());
    }

    private Map<Long, String> findShopNames(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        return sellerService
                .findShopProfiles(orders.stream().map(Order::getSellerId).distinct().toList())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().shopName()));
    }

    private record CursorPage(List<Order> orders, Long nextCursor, boolean hasNext) {}

    // oneMoreThanPage가 더 읽어둔 한 장으로 다음이 있는지 가린다.
    private CursorPage toCursorPage(List<Order> found) {
        boolean hasNext = found.size() > ORDER_PAGE_SIZE;
        List<Order> orders = hasNext ? found.subList(0, ORDER_PAGE_SIZE) : found;
        return new CursorPage(
                orders, hasNext ? orders.get(orders.size() - 1).getId() : null, hasNext);
    }

    private Long cursorOf(Long cursor) {
        return cursor == null ? FIRST_PAGE_CURSOR : cursor;
    }

    private OrderCountsResponse countCustomerOrders(Long customerId) {
        return toCounts(orderRepository.countByCustomerIdGroupByStatus(customerId));
    }

    /** 셀러 주문 상세 화면을 채운다. */
    @Transactional(readOnly = true)
    public SellerOrderDetailResponse findSellerOrder(Long orderId, Long sellerId) {
        return SellerOrderDetailResponse.from(requireSellerOrder(orderId, sellerId));
    }

    /** 택배사 드롭다운을 채운다. */
    public List<CourierResponse> findCouriers() {
        return Arrays.stream(Courier.values()).map(CourierResponse::from).toList();
    }

    /**
     * 주문하기. 재고를 먼저 선점해 주문을 만들고, 그 주문에 결제 세션을 붙여 결제창을 열 수 있게 한다.
     *
     * <p>재고가 모자라면 결제창을 열기 전에 거절한다. 재고가 2개인 상품을 3명이 동시에 사면 마지막 한 명은 여기서 실패한다.
     *
     * <p>payerId는 프론트가 결제창 인증 URL에 실어 보낸다. point3 세션 생성 본문은 이 값을 받지 않는다.
     */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 세션을 만들지 못하면 주문을 결제실패로 남기고 재고를 되돌린다.
    public OrderCreateResponse createOrder(OrderCreateCommand command) {
        // 재고를 잡기 전에 읽어 둔다. 여기서 실패하면 되돌릴 선점이 없다.
        String payerId = customerService.findPayerId(command.customerId());

        OrderCreateResponse holding = resumeHoldingOrder(command, payerId);
        if (holding != null) {
            return holding;
        }

        ReservedProduct product =
                productService.reserveForOrder(command.productId(), command.quantity());
        Order order = savePendingOrder(command, product);
        try {
            String sessionId =
                    paymentService.createPurchaseSession(
                            new PurchaseSessionCommand(
                                    command.userId(),
                                    order.getId(),
                                    order.getTotalAmount(),
                                    order.getProductName()));
            transactionTemplate.executeWithoutResult(
                    status -> readOrder(order.getId()).linkSession(sessionId));
            return new OrderCreateResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    sessionId,
                    order.getTotalAmount(),
                    payerId,
                    order.holdExpiresAt());
        } catch (RuntimeException e) {
            failPayment(order.getId(), SESSION_CREATE_FAILED_REASON);
            throw e;
        }
    }

    /** 결제하다 자리를 비운 사람이 다시 눌렀을 때, 잡아 둔 주문을 그대로 이어서 결제하게 한다. */
    // 재고를 또 선점하지 않는다. 선점이 풀린 뒤라면 새 주문으로 넘겨 다른 사람과 같은 선에서 다투게 한다.
    private OrderCreateResponse resumeHoldingOrder(OrderCreateCommand command, String payerId) {
        return orderRepository
                .findFirstByCustomerIdAndProductIdAndStatusOrderByIdDesc(
                        command.customerId(), command.productId(), OrderStatus.PAYMENT_PENDING)
                .filter(order -> order.isHoldAlive(LocalDateTime.now()))
                .filter(order -> order.getSessionId() != null)
                .map(
                        order ->
                                new OrderCreateResponse(
                                        order.getId(),
                                        order.getOrderNumber(),
                                        order.getSessionId(),
                                        order.getTotalAmount(),
                                        payerId,
                                        order.holdExpiresAt()))
                .orElse(null);
    }

    /** 결제창만 열어 두고 끝내지 않은 주문의 선점을 풀어, 기다리던 다른 사람이 살 수 있게 한다. */
    // 한 건이 실패해도 나머지는 푼다. 푼 뒤에 결제가 승인되면 승인 쪽에서 취소로 되돌린다.
    public void releaseExpiredCheckoutHolds() {
        List<Order> expired =
                orderRepository.findByStatusAndCreatedAtBefore(
                        OrderStatus.PAYMENT_PENDING,
                        LocalDateTime.now().minus(Order.CHECKOUT_HOLD));
        for (Order order : expired) {
            try {
                failPayment(order.getId(), CHECKOUT_HOLD_EXPIRED_REASON);
                log.info("결제를 끝내지 않아 선점을 풀었다 - orderId={}", order.getId());
            } catch (RuntimeException e) {
                log.warn("선점을 풀지 못했다 - orderId={}", order.getId(), e);
            }
        }
    }

    /**
     * 결제 세션을 승인해 주문을 배송대기로 넘긴다. 이미 결제가 끝난 주문은 그 결과를 그대로 돌려준다.
     *
     * <p>승인이 거절되면 주문을 결제실패로 남기고 선점했던 재고를 되돌린 뒤 거절을 알린다. 결과를 확인하지 못한 경우는 주문을 결제 대기로 남겨, 다시 불러 확인할 수
     * 있게 한다.
     */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다.
    public OrderPaymentResponse payOrder(Long orderId, Long customerId) {
        Order order = readOwnOrder(orderId, customerId);
        if (order.isPaid()) {
            return new OrderPaymentResponse(order.getId(), order.getStatus(), order.getPaidAt());
        }
        requirePayable(order);

        PaymentCaptureResult result =
                paymentService.capture(order.getSessionId(), order.getTotalAmount());
        if (!result.captured()) {
            failPayment(orderId, result.failureMessage());
            throw new CustomException(PaymentErrorCode.PAYMENT_REJECTED);
        }
        return confirmPaid(order, result.capturedAt());
    }

    /**
     * 결제까지 끝난 주문을 서버가 되돌린다. 결제금액을 취소하고 선점했던 재고를 되돌린다.
     *
     * <p>구매자가 부르는 길은 없다. 보낼 수 없게 된 주문을 서버가 정리할 때만 쓴다.
     */
    // point3를 호출하므로 트랜잭션 밖에서 쓴다. 이미 보낸 주문은 되돌리지 않는다.
    public void cancelPaidOrder(Long orderId, String reason) {
        Order order = readOrder(orderId);
        if (!order.isPaid() || order.isShipped()) {
            throw new CustomException(OrderErrorCode.ORDER_NOT_CANCELABLE);
        }
        paymentService.refund(
                new PaymentRefundCommand(
                        orderId, order.getSessionId(), order.getTotalAmount(), reason));
        transactionTemplate.executeWithoutResult(
                status -> {
                    Order found = readOrderForUpdate(orderId);
                    found.cancel(reason);
                    productService.releaseForOrder(found.getProductId(), found.getQuantity());
                });
        log.info("결제까지 끝난 주문을 되돌렸다 - orderId={}, reason={}", orderId, reason);
    }

    // 재고를 선점한 뒤라, 주문을 만들지 못하면 그 자리에서 되돌린다.
    private Order savePendingOrder(OrderCreateCommand command, ReservedProduct product) {
        try {
            return transactionTemplate.execute(
                    status ->
                            orderRepository.save(
                                    Order.createPending(
                                            newOrderNumber(),
                                            command,
                                            product,
                                            customerService.findShippingDestination(
                                                    command.customerId()),
                                            sellerService.calculateShippingFee(
                                                    product.sellerId(),
                                                    product.price() * command.quantity()))));
        } catch (RuntimeException e) {
            productService.releaseForOrder(command.productId(), command.quantity());
            throw e;
        }
    }

    // 승인을 기다리는 동안 주문이 되돌려졌을 수 있어, 상태를 잠그고 다시 확인한 뒤에 결제를 확정한다.
    private OrderPaymentResponse confirmPaid(Order order, LocalDateTime capturedAt) {
        boolean reverted =
                Boolean.TRUE.equals(
                        transactionTemplate.execute(
                                status -> {
                                    Order found = readOrderForUpdate(order.getId());
                                    if (found.isPaid()) {
                                        return false;
                                    }
                                    if (!found.isPaymentPending()) {
                                        return true;
                                    }
                                    found.pay(capturedAt);
                                    return false;
                                }));
        if (reverted) {
            refundRevertedOrder(order.getId());
            throw new CustomException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        return new OrderPaymentResponse(order.getId(), OrderStatus.SHIPPING_PENDING, capturedAt);
    }

    /** 결제가 승인되지 않은 주문을 결제실패로 남기고 선점했던 재고를 되돌린다. */
    // 같은 주문에 두 번 들어와도 재고를 두 번 되돌리지 않도록 상태를 잠그고 확인한다.
    private void failPayment(Long orderId, String reason) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    Order order = readOrderForUpdate(orderId);
                    if (!order.isPaymentPending()) {
                        return;
                    }
                    order.failPayment(reason);
                    productService.releaseForOrder(order.getProductId(), order.getQuantity());
                });
    }

    // 이미 되돌린 주문에 승인이 늦게 성공한 경우다. 재고는 되돌려 둔 상태라 결제금액만 취소한다.
    private void refundRevertedOrder(Long orderId) {
        Order order = readOrder(orderId);
        log.error("되돌린 주문에 결제가 승인돼 결제금액을 취소한다 - orderId={}, status={}", orderId, order.getStatus());
        paymentService.refund(
                new PaymentRefundCommand(
                        orderId,
                        order.getSessionId(),
                        order.getTotalAmount(),
                        REVERTED_ORDER_REFUND_REASON));
        transactionTemplate.executeWithoutResult(
                status -> readOrderForUpdate(orderId).cancel(REVERTED_ORDER_REFUND_REASON));
    }

    private void requirePayable(Order order) {
        if (!order.isPaymentPending()) {
            throw new CustomException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        if (order.getSessionId() == null) {
            throw new CustomException(OrderErrorCode.ORDER_SESSION_NOT_LINKED);
        }
    }

    private Order readOwnOrder(Long orderId, Long customerId) {
        return orderRepository
                .findById(orderId)
                .filter(found -> found.isOwnedByCustomer(customerId))
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private Order readOrder(Long orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private Order readOrderForUpdate(Long orderId) {
        return orderRepository
                .findForUpdateById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 주문번호가 겹치면 저장이 막히므로, 날짜만으로 겹치지 않도록 뒤에 무작위 값을 붙인다.
    private String newOrderNumber() {
        return LocalDate.now().format(ORDER_NUMBER_DATE)
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, ORDER_NUMBER_SUFFIX_LENGTH)
                        .toUpperCase();
    }

    /** 셀러가 운송장을 등록해 주문을 발송완료로 넘긴다. */
    @Transactional
    public void registerShipment(OrderShipmentCommand command) {
        Order order =
                ownedBySeller(
                        orderRepository.findForUpdateById(command.orderId()), command.sellerId());
        if (order.isShipped()) {
            throw new CustomException(OrderErrorCode.ORDER_ALREADY_SHIPPED);
        }
        order.ship(command.courier(), command.trackingNumber());
        eventPublisher.publishEvent(OrderShippedEvent.from(order));
    }

    private Order requireSellerOrder(Long orderId, Long sellerId) {
        return ownedBySeller(orderRepository.findById(orderId), sellerId);
    }

    // 남의 주문 번호로는 통과할 수 없다.
    private Order ownedBySeller(Optional<Order> found, Long sellerId) {
        return found.filter(order -> order.isOwnedBySeller(sellerId))
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 다음이 있는지는 한 장을 더 읽어서 가린다.
    private PageRequest oneMoreThanPage() {
        return PageRequest.of(0, ORDER_PAGE_SIZE + 1);
    }

    private OrderCountsResponse countSellerOrders(Long sellerId) {
        return toCounts(orderRepository.countBySellerIdGroupByStatus(sellerId));
    }

    private OrderCountsResponse toCounts(List<OrderStatusCount> counts) {
        Map<OrderStatus, Integer> counted =
                counts.stream()
                        .collect(
                                Collectors.toMap(
                                        OrderStatusCount::getStatus,
                                        OrderStatusCount::getOrderCount));
        int shippingPending = counted.getOrDefault(OrderStatus.SHIPPING_PENDING, 0);
        int shipped = counted.getOrDefault(OrderStatus.SHIPPED, 0);
        return new OrderCountsResponse(shippingPending + shipped, shippingPending, shipped);
    }
}
