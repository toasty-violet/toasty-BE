create table addresses
(
    id             bigint       not null auto_increment,
    user_id        bigint       not null,
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
    key idx_addresses_user_id (user_id),
    constraint fk_addresses_user_id foreign key (user_id) references users (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
