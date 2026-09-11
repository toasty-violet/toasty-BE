-- 표시명을 역할별 테이블로 옮긴다. users에는 계정 식별과 역할만 남는다.
-- 구매자 닉네임과 판매자 상점명은 서로 다른 이름 공간이라, 같은 값을 각각 쓸 수 있다.

alter table customers
    add column nickname varchar(20) null after user_id;

alter table sellers
    add column shop_name varchar(20) null after user_id;

update customers c
    join users u on u.id = c.user_id
set c.nickname = u.nickname;

update sellers s
    join users u on u.id = s.user_id
set s.shop_name = u.nickname;

alter table customers
    modify column nickname varchar(20) not null,
    add constraint uk_customers_nickname unique (nickname);

alter table sellers
    modify column shop_name varchar(20) not null,
    add constraint uk_sellers_shop_name unique (shop_name);

alter table users
    drop index uk_users_nickname,
    drop column nickname;
