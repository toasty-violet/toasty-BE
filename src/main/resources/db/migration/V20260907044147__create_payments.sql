-- point3 결제 세션 원장. 세션을 만든 목적을 함께 남겨 승인 여부를 서버에서 판단한다.
create table payments
(
    id         bigint       not null auto_increment,
    user_id    bigint       not null,
    session_id varchar(100) not null,
    purpose    varchar(30)  not null,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id),
    unique key uk_payments_session_id (session_id),
    constraint fk_payments_user_id foreign key (user_id) references users (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
