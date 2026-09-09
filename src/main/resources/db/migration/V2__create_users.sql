create table users
(
    id             bigint       not null auto_increment,
    kakao_id       varchar(50)  not null,
    user_type      varchar(20)  not null,
    phone_number   varchar(20)  not null,
    name           varchar(50)  not null,
    nickname       varchar(50)  null,
    postal_code    varchar(10)  null,
    address        varchar(255) null,
    payment_linked boolean      not null default false,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (id),
    unique key uk_users_kakao_id (kakao_id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
