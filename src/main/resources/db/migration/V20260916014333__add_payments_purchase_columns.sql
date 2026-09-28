-- 상품 결제 세션은 승인 결과까지 남긴다.
-- payerId 확보용 세션은 승인을 호출하지 않아 이 컬럼들이 모두 비어 있다.
alter table payments
    add column order_id bigint null after user_id,
    add column amount int null after session_id,
    add column status varchar(20) null after amount,
    add column captured_at datetime(6) null after status,
    add column outcome_code varchar(50) null after captured_at,
    add column failure_message varchar(255) null after outcome_code,
    add key idx_payments_order_id (order_id),
    add constraint fk_payments_order_id foreign key (order_id) references orders (id);
