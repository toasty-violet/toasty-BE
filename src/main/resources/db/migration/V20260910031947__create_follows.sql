-- 구매자가 판매자를 팔로우한 관계. 방향은 구매자 -> 판매자 한쪽뿐이라,
-- 컬럼이 참조하는 테이블로 방향이 고정되어 구매자끼리나 판매자끼리는 팔로우할 수 없다.
-- 유저 탈퇴는 소프트 삭제라 외래키 cascade가 돌지 않아, 탈퇴 시 정리는 서비스에서 직접 지운다.

create table follows
(
    id          bigint      not null auto_increment,
    customer_id bigint      not null,
    seller_id   bigint      not null,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    -- 같은 판매자를 두 번 팔로우하는 것을 막고, 구매자의 팔로잉 목록 조회도 이 인덱스로 처리한다
    unique key uk_follows_customer_id_seller_id (customer_id, seller_id),
    -- 판매자의 팔로워 목록과 팔로워 수 조회에 쓴다
    key idx_follows_seller_id (seller_id),
    constraint fk_follows_customer_id foreign key (customer_id) references customers (id),
    constraint fk_follows_seller_id foreign key (seller_id) references sellers (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
