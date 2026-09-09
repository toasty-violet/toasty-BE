-- 온보딩 정보를 구매자·판매자로 나눈다. users에는 계정 식별과 역할, 공통 표시명만 남는다.
-- nickname은 구매자에게는 닉네임, 판매자에게는 상점명으로 쓴다.

create table customers
(
    id           bigint      not null auto_increment,
    user_id      bigint      not null,
    name         varchar(50) null,
    phone_number varchar(20) null,
    payer_id     varchar(50) null,
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    primary key (id),
    unique key uk_customers_user_id (user_id),
    constraint fk_customers_user_id foreign key (user_id) references users (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;

create table sellers
(
    id              bigint       not null auto_increment,
    user_id         bigint       not null,
    shop_image_url  varchar(500) null,
    description     varchar(500) null,
    seller_name     varchar(50)  null,
    phone_number    varchar(20)  null,
    business_number varchar(10)  null,
    bank            varchar(20)  null,
    account_number  varchar(30)  null,
    created_at      datetime(6)  not null,
    updated_at      datetime(6)  not null,
    primary key (id),
    unique key uk_sellers_user_id (user_id),
    unique key uk_sellers_business_number (business_number),
    constraint fk_sellers_user_id foreign key (user_id) references users (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;

-- 역할이 정해진 기존 유저에게 프로필 행을 만든다. 역할 선택 시 행이 생기는 규칙에 기존 데이터를 맞춘다.
insert into customers (user_id, name, phone_number, payer_id, created_at, updated_at)
select id, name, phone_number, payer_id, created_at, now(6)
from users
where role = 'CUSTOMER';

insert into sellers (user_id, created_at, updated_at)
select id, created_at, now(6)
from users
where role = 'SELLER';

-- lives의 셀러 참조를 users.id에서 sellers.id로 옮긴다.
-- active_seller_id의 unique 인덱스는 값을 바꾸는 도중 일시적으로 충돌할 수 있어 떼었다가 다시 붙인다.
alter table lives
    drop foreign key fk_lives_seller_id,
    drop index uk_lives_active_seller_id;

update lives l
    join sellers owner on owner.user_id = l.seller_id
    left join sellers active on active.user_id = l.active_seller_id
set l.seller_id        = owner.id,
    l.active_seller_id = active.id;

alter table lives
    add constraint uk_lives_active_seller_id unique (active_seller_id),
    add constraint fk_lives_seller_id foreign key (seller_id) references sellers (id);

alter table users
    drop column name,
    drop column phone_number,
    drop column payer_id;
