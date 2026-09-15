-- 주문을 결제 전에 먼저 만들고 결제 결과로 상태를 옮기므로, 결제가 끝나지 않은 주문도 담을 수 있게 고친다.
-- 재고를 되돌릴 상품과 승인·취소에 쓸 세션을 주문에 붙여 둔다.
alter table orders
    add column product_id bigint null after seller_id,
    add column session_id varchar(100) null after status,
    add column canceled_at datetime(6) null after paid_at,
    add column payment_failure_reason varchar(255) null after canceled_at,
    modify column paid_at datetime(6) null,
    add unique key uk_orders_session_id (session_id);
