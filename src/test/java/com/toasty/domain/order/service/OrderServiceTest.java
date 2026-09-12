package com.toasty.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.order.controller.dto.response.SellerOrderDetailResponse;
import com.toasty.domain.order.controller.dto.response.SellerOrdersResponse;
import com.toasty.domain.order.entity.Order;
import com.toasty.domain.order.entity.OrderStatus;
import com.toasty.domain.order.entity.SellerOrderFilter;
import com.toasty.domain.order.entity.SellerOrderPageCommand;
import com.toasty.domain.order.exception.OrderErrorCode;
import com.toasty.domain.order.repository.OrderRepository;
import com.toasty.domain.order.repository.SellerOrderCount;
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

@DisplayName("셀러 주문탭")
class OrderServiceTest {

    private static final Long SELLER_ID = 7L;
    private static final int PAGE_SIZE = 20;

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = new OrderService(orderRepository);
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

    private SellerOrderCount count(OrderStatus status, int orderCount) {
        return new SellerOrderCount() {
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
                            new SellerOrderPageCommand(SELLER_ID, SellerOrderFilter.ALL, null));

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
                            new SellerOrderPageCommand(SELLER_ID, SellerOrderFilter.ALL, 30L));

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
                            new SellerOrderPageCommand(SELLER_ID, SellerOrderFilter.ALL, null));

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
                            new SellerOrderPageCommand(SELLER_ID, SellerOrderFilter.ALL, null));

            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
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
}
