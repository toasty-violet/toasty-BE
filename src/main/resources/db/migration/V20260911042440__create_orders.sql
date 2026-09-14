-- 주문 원장. 장바구니가 없어 한 번에 하나씩 사므로 주문 하나에 상품도 하나다.
-- 상품과 배송지는 나중에 지워지거나 바뀌므로, 주문 시점 값을 복사해 둔다.
create table orders
(
    id                bigint       not null auto_increment,
    order_number      varchar(30)  not null,
    customer_id       bigint       not null,
    seller_id         bigint       not null,
    product_name      varchar(200) not null,
    product_image_url varchar(500) null,
    product_price     int          not null,
    quantity          int          not null,
    shipping_fee      int          not null,
    total_amount      int          not null,
    status            varchar(20)  not null,
    paid_at           datetime(6)  not null,
    courier           varchar(30)  null,
    tracking_number   varchar(50)  null,
    shipped_at        datetime(6)  null,
    receiver_name     varchar(50)  not null,
    receiver_phone    varchar(20)  not null,
    postal_code       varchar(10)  not null,
    address           varchar(255) not null,
    detail_address    varchar(255) null,
    created_at        datetime(6)  not null,
    updated_at        datetime(6)  not null,
    primary key (id),
    unique key uk_orders_order_number (order_number),
    -- 셀러 주문탭이 자기 주문을 최신순으로 커서를 옮겨가며 읽는다.
    key idx_orders_seller_id_id (seller_id, id),
    constraint fk_orders_customer_id foreign key (customer_id) references customers (id),
    constraint fk_orders_seller_id foreign key (seller_id) references sellers (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
