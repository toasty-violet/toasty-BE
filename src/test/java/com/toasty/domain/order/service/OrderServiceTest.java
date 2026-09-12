package com.toasty.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.order.controller.dto.response.CourierResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrderResponse;
import com.toasty.domain.order.controller.dto.response.CustomerOrdersResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrdersResponse;
import com.toasty.domain.order.entity.Courier;
import com.toasty.domain.order.entity.CustomerOrderPageCommand;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderShipmentCommand;
import com.toasty.domain.order.entity.OrderStatus;
import com.toasty.domain.order.entity.OrderStatusFilter;
import com.toasty.domain.order.entity.SellerOrderPageCommand;
import com.toasty.domain.order.exception.OrderErrorCode;
import com.toasty.domain.order.repository.OrderRepository;
import com.toasty.domain.order.repository.OrderStatusCount;
import com.toasty.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("주문")
class OrderServiceTest {

    private static final Long SELLER_ID = 7L;
    private static final int PAGE_SIZE = 20;

    private OrderRepository orderRepository;
    private com.toasty.domain.seller.service.SellerService sellerService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        sellerService = mock(com.toasty.domain.seller.service.SellerService.class);
        orderService = new OrderService(orderRepository, sellerService);
    }

    // 주문을 만드는 것은 결제 흐름의 몫이라 이 도메인에 팩터리가 없다. 테스트에서만 빈 인스턴스를 세운다.
    private Order order(Long orderId, Long sellerId, OrderStatus status) {
        Order order = org.springframework.beans.BeanUtils.instantiateClass(Order.class);
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "orderNumber", "20260915-10023" + orderId);
        ReflectionTestUtils.setField(order, "sellerId", sellerId);
        ReflectionTestUtils.setField(order, "customerId", 1L);
        ReflectionTestUtils.setField(order, "productName", "아이보리 골지 가디건");
        ReflectionTestUtils.setField(order, "quantity", 1);
        ReflectionTestUtils.setField(order, "productPrice", 27000);
        ReflectionTestUtils.setField(order, "shippingFee", 3000);
        ReflectionTestUtils.setField(order, "totalAmount", 30000);
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "paidAt", LocalDateTime.now());
        ReflectionTestUtils.setField(order, "receiverName", "이현지");
        ReflectionTestUtils.setField(order, "receiverPhone", "010-2345-6789");
        ReflectionTestUtils.setField(order, "postalCode", "06236");
        ReflectionTestUtils.setField(order, "address", "서울시 강남구 테헤란로 123");
        return order;
    }

    private OrderStatusCount count(OrderStatus status, int orderCount) {
        return new OrderStatusCount() {
            @Override
            public OrderStatus getStatus() {
                return status;
            }

            @Override
            public int getOrderCount() {
                return orderCount;
            }
        };
    }

    @Nested
    @DisplayName("목록")
    class FindSellerOrders {

        private void givenFound(int count) {
            given(
                            orderRepository.findBySellerIdAndStatusInAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(
                            IntStream.rangeClosed(1, count)
                                    .mapToObj(
                                            i ->
                                                    order(
                                                            (long) i,
                                                            SELLER_ID,
                                                            OrderStatus.SHIPPING_PENDING))
                                    .toList());
        }

        @Test
        @DisplayName("첫 요청에는 상태 칩 건수를 함께 내린다")
        void 첫_요청은_건수를_준다() {
            givenFound(2);
            given(orderRepository.countBySellerIdGroupByStatus(SELLER_ID))
                    .willReturn(
                            List.of(
                                    count(OrderStatus.SHIPPING_PENDING, 12),
                                    count(OrderStatus.SHIPPED, 31)));

            SellerOrdersResponse response =
                    orderService.findSellerOrders(
                            new SellerOrderPageCommand(SELLER_ID, OrderStatusFilter.ALL, null));

            assertThat(response.counts().all()).isEqualTo(43);
            assertThat(response.counts().shippingPending()).isEqualTo(12);
            assertThat(response.counts().shipped()).isEqualTo(31);
        }

        @Test
        @DisplayName("이어 받을 때는 건수를 세지 않는다")
        void 이어_받으면_건수를_안_센다() {
            givenFound(2);

            SellerOrdersResponse response =
                    orderService.findSellerOrders(
                            new SellerOrderPageCommand(SELLER_ID, OrderStatusFilter.ALL, 30L));

            assertThat(response.counts()).isNull();
            verify(orderRepository, never()).countBySellerIdGroupByStatus(any());
        }

        @Test
        @DisplayName("한 장을 더 읽어 다음이 있는지 보고, 넘치는 건 잘라낸다")
        void 다음_페이지를_안다() {
            givenFound(PAGE_SIZE + 1);
            given(orderRepository.countBySellerIdGroupByStatus(any())).willReturn(List.of());

            SellerOrdersResponse response =
                    orderService.findSellerOrders(
                            new SellerOrderPageCommand(SELLER_ID, OrderStatusFilter.ALL, null));

            assertThat(response.items()).hasSize(PAGE_SIZE);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(PAGE_SIZE);
        }

        @Test
        @DisplayName("마지막 묶음이면 커서를 주지 않는다")
        void 마지막이면_커서가_없다() {
            givenFound(3);
            given(orderRepository.countBySellerIdGroupByStatus(any())).willReturn(List.of());

            SellerOrdersResponse response =
                    orderService.findSellerOrders(
                            new SellerOrderPageCommand(SELLER_ID, OrderStatusFilter.ALL, null));

            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("구매자 주문내역")
    class CustomerOrders {

        private static final Long CUSTOMER_ID = 1L;

        private void givenFound(int count) {
            given(
                            orderRepository.findByCustomerIdAndStatusInAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(
                            IntStream.rangeClosed(1, count)
                                    .mapToObj(
                                            i ->
                                                    order(
                                                            (long) i,
                                                            SELLER_ID,
                                                            OrderStatus.SHIPPING_PENDING))
                                    .toList());
            given(sellerService.findShopProfiles(any()))
                    .willReturn(
                            java.util.Map.of(
                                    SELLER_ID,
                                    new com.toasty.domain.seller.controller.dto.response
                                            .SellerProfileResponse(SELLER_ID, "토스티샵", null)));
        }

        @Test
        @DisplayName("스토어 이름을 붙여 준다")
        void 스토어_이름을_붙인다() {
            givenFound(2);
            given(orderRepository.countByCustomerIdGroupByStatus(CUSTOMER_ID))
                    .willReturn(List.of(count(OrderStatus.SHIPPING_PENDING, 2)));

            CustomerOrdersResponse response =
                    orderService.findCustomerOrders(
                            new CustomerOrderPageCommand(CUSTOMER_ID, OrderStatusFilter.ALL, null));

            assertThat(response.items())
                    .extracting(CustomerOrderResponse::shopName)
                    .containsOnly("토스티샵");
            assertThat(response.counts().all()).isEqualTo(2);
        }

        @Test
        @DisplayName("스토어가 겹쳐도 한 번에 모아 읽는다")
        void 스토어를_모아_읽는다() {
            givenFound(3);
            given(orderRepository.countByCustomerIdGroupByStatus(any())).willReturn(List.of());

            orderService.findCustomerOrders(
                    new CustomerOrderPageCommand(CUSTOMER_ID, OrderStatusFilter.ALL, null));

            verify(sellerService).findShopProfiles(List.of(SELLER_ID));
        }

        @Test
        @DisplayName("이어 받을 때는 건수를 세지 않는다")
        void 이어_받으면_건수를_안_센다() {
            givenFound(2);

            CustomerOrdersResponse response =
                    orderService.findCustomerOrders(
                            new CustomerOrderPageCommand(CUSTOMER_ID, OrderStatusFilter.ALL, 30L));

            assertThat(response.counts()).isNull();
            verify(orderRepository, never()).countByCustomerIdGroupByStatus(any());
        }

        @Test
        @DisplayName("주문이 없으면 스토어도 읽지 않는다")
        void 없으면_스토어를_안_읽는다() {
            given(
                            orderRepository.findByCustomerIdAndStatusInAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(List.of());
            given(orderRepository.countByCustomerIdGroupByStatus(any())).willReturn(List.of());

            CustomerOrdersResponse response =
                    orderService.findCustomerOrders(
                            new CustomerOrderPageCommand(CUSTOMER_ID, OrderStatusFilter.ALL, null));

            assertThat(response.items()).isEmpty();
            verify(sellerService, never()).findShopProfiles(any());
        }

        @Test
        @DisplayName("남의 주문이면 ORDER_NOT_FOUND다")
        void 남의_주문은_못_본다() {
            given(orderRepository.findById(31L))
                    .willReturn(Optional.of(order(31L, SELLER_ID, OrderStatus.SHIPPING_PENDING)));

            assertThatThrownBy(() -> orderService.findCustomerOrder(31L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상세")
    class FindSellerOrder {

        @Test
        @DisplayName("주문 시점 값을 그대로 준다")
        void 주문_시점_값을_준다() {
            given(orderRepository.findById(31L))
                    .willReturn(Optional.of(order(31L, SELLER_ID, OrderStatus.SHIPPING_PENDING)));

            SellerOrderDetailResponse detail = orderService.findSellerOrder(31L, SELLER_ID);

            assertThat(detail.productName()).isEqualTo("아이보리 골지 가디건");
            assertThat(detail.receiverName()).isEqualTo("이현지");
            assertThat(detail.productPrice()).isEqualTo(27000);
            assertThat(detail.shippingFee()).isEqualTo(3000);
            assertThat(detail.totalAmount()).isEqualTo(30000);
        }

        @Test
        @DisplayName("남의 주문이면 ORDER_NOT_FOUND다")
        void 남의_주문은_못_본다() {
            given(orderRepository.findById(31L))
                    .willReturn(Optional.of(order(31L, 99L, OrderStatus.SHIPPING_PENDING)));

            assertThatThrownBy(() -> orderService.findSellerOrder(31L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 주문이면 ORDER_NOT_FOUND다")
        void 없으면_못_본다() {
            given(orderRepository.findById(31L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findSellerOrder(31L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("운송장 등록")
    class RegisterShipment {

        private OrderShipmentCommand command(Long orderId, Long sellerId) {
            return new OrderShipmentCommand(
                    orderId, sellerId, Courier.CJ_LOGISTICS, "394817503811");
        }

        @Test
        @DisplayName("등록하면 발송완료로 넘어가고 운송장이 남는다")
        void 발송완료로_넘어간다() {
            Order found = order(31L, SELLER_ID, OrderStatus.SHIPPING_PENDING);
            given(orderRepository.findById(31L)).willReturn(Optional.of(found));

            orderService.registerShipment(command(31L, SELLER_ID));

            assertThat(found.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(found.getCourier()).isEqualTo(Courier.CJ_LOGISTICS);
            assertThat(found.getTrackingNumber()).isEqualTo("394817503811");
            assertThat(found.getShippedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 발송완료면 ORDER_ALREADY_SHIPPED다")
        void 두_번_등록할_수_없다() {
            Order found = order(31L, SELLER_ID, OrderStatus.SHIPPED);
            ReflectionTestUtils.setField(found, "courier", Courier.HANJIN);
            ReflectionTestUtils.setField(found, "trackingNumber", "111111111111");
            given(orderRepository.findById(31L)).willReturn(Optional.of(found));

            assertThatThrownBy(() -> orderService.registerShipment(command(31L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_ALREADY_SHIPPED);
            assertThat(found.getTrackingNumber()).isEqualTo("111111111111");
        }

        @Test
        @DisplayName("남의 주문에는 등록할 수 없다")
        void 남의_주문은_못_건드린다() {
            Order found = order(31L, 99L, OrderStatus.SHIPPING_PENDING);
            given(orderRepository.findById(31L)).willReturn(Optional.of(found));

            assertThatThrownBy(() -> orderService.registerShipment(command(31L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
            assertThat(found.getStatus()).isEqualTo(OrderStatus.SHIPPING_PENDING);
        }

        @Test
        @DisplayName("없는 주문이면 ORDER_NOT_FOUND다")
        void 없으면_못_등록한다() {
            given(orderRepository.findById(31L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.registerShipment(command(31L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("상세는 택배사를 코드가 아니라 이름으로 준다")
        void 택배사를_이름으로_준다() {
            Order found = order(31L, SELLER_ID, OrderStatus.SHIPPING_PENDING);
            given(orderRepository.findById(31L)).willReturn(Optional.of(found));
            orderService.registerShipment(command(31L, SELLER_ID));

            SellerOrderDetailResponse detail = orderService.findSellerOrder(31L, SELLER_ID);

            assertThat(detail.courierName()).isEqualTo("CJ 대한통운");
            assertThat(detail.trackingNumber()).isEqualTo("394817503811");
        }

        @Test
        @DisplayName("배송대기 주문의 택배사 자리는 비어 있다")
        void 등록_전에는_비어_있다() {
            given(orderRepository.findById(31L))
                    .willReturn(Optional.of(order(31L, SELLER_ID, OrderStatus.SHIPPING_PENDING)));

            SellerOrderDetailResponse detail = orderService.findSellerOrder(31L, SELLER_ID);

            assertThat(detail.courierName()).isNull();
            assertThat(detail.trackingNumber()).isNull();
        }

        @Test
        @DisplayName("택배사 목록은 등록에 넣을 코드와 화면에 쓸 이름을 함께 준다")
        void 택배사_목록을_준다() {
            List<CourierResponse> couriers = orderService.findCouriers();

            assertThat(couriers).hasSize(Courier.values().length);
            assertThat(couriers)
                    .extracting(CourierResponse::code)
                    .containsExactly("CJ_LOGISTICS", "HANJIN", "LOTTE", "LOGEN", "POST_OFFICE");
            assertThat(couriers.get(0).name()).isEqualTo("CJ 대한통운");
        }
    }
}
