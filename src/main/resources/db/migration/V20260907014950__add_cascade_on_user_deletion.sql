-- 유저를 지우면 구매자·판매자 정보와 배송지가 함께 지워지도록 외래키에 cascade를 건다.
-- 판매자의 상품·라이브는 그대로 남아, 상품이나 라이브가 있는 판매자는 삭제되지 않는다.

alter table customers
    drop foreign key fk_customers_user_id;

alter table customers
    add constraint fk_customers_user_id foreign key (user_id) references users (id) on delete cascade;

alter table sellers
    drop foreign key fk_sellers_user_id;

alter table sellers
    add constraint fk_sellers_user_id foreign key (user_id) references users (id) on delete cascade;

alter table addresses
    drop foreign key fk_addresses_customer_id;

alter table addresses
    add constraint fk_addresses_customer_id foreign key (customer_id) references customers (id) on delete cascade;
