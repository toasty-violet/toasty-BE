-- 구매자의 배송지. 카카오 우편번호 서비스가 내려준 값과 유저가 입력한 상세주소를 담는다.
create table addresses
(
    id             bigint       not null auto_increment,
    customer_id    bigint       not null,
    postal_code    varchar(10)  not null,
    road_address   varchar(255) null,
    jibun_address  varchar(255) null,
    address_type   varchar(10)  not null,
    building_name  varchar(100) null,
    legal_dong     varchar(50)  null,
    detail_address varchar(255) null,
    is_default     boolean      not null default false,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (id),
    key idx_addresses_customer_id (customer_id),
    constraint fk_addresses_customer_id foreign key (customer_id) references customers (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
