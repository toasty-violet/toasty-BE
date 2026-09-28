-- point3에 보낸 취소 요청 한 건. 부분 취소가 있어 주문 하나에 여러 건이 쌓인다.
-- 세금은 point3가 계산해 주지 않아 우리가 보낸 값을 그대로 남긴다.
create table refunds
(
    id              bigint       not null auto_increment,
    order_id        bigint       not null,
    session_id      varchar(100) not null,
    -- point3가 돌려준 취소 건 식별자. 응답을 받지 못한 요청은 비어 있다
    refund_id       varchar(100) null,
    amount          int          not null,
    tax_free_amount int          not null,
    vat             int          not null,
    reason          varchar(200) not null,
    status          varchar(20)  not null,
    -- 같은 취소를 두 번 보내지 않도록 요청마다 정해 두는 값. point3가 24시간 동안 기억한다
    idempotency_key varchar(100) not null,
    created_at      datetime(6)  not null,
    updated_at      datetime(6)  not null,
    primary key (id),
    unique key uk_refunds_idempotency_key (idempotency_key),
    key idx_refunds_order_id (order_id),
    constraint fk_refunds_order_id foreign key (order_id) references orders (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
