package com.toasty.domain.order.service;

import com.toasty.domain.order.controller.dto.response.CustomerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrderResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrdersResponse;
import com.toasty.domain.order.controller.dto.response.OrderCountsResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrderResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrdersResponse;
import com.toasty.domain.order.entity.CustomerOrderPageCommand;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import com.toasty.domain.order.entity.SellerOrderPageCommand;
import com.toasty.domain.order.exception.OrderErrorCode;
import com.toasty.domain.order.repository.OrderRepository;
import com.toasty.domain.order.repository.OrderStatusCount;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 셀러가 자기 주문을 훑고 한 건을 자세히 본다. */
@Service
@RequiredArgsConstructor
public class OrderService {

    // 주문탭이 한 번에 당겨오는 개수. 무한스크롤이다.
    private static final int ORDER_PAGE_SIZE = 20;

    // 첫 페이지는 커서가 없다. id는 양수라 최댓값을 넣으면 맨 앞부터 읽는다.
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;

    private final OrderRepository orderRepository;
    private final SellerService sellerService;

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
        Order order =
                orderRepository
                        .findById(orderId)
                        .filter(found -> found.isOwnedBySeller(sellerId))
                        .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
        return SellerOrderDetailResponse.from(order);
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
