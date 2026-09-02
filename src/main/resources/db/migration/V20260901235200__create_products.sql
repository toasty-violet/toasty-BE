create table products
(
    id             bigint       not null auto_increment,
    seller_id      bigint       not null,
    name           varchar(200) not null,
    price          int          not null,
    stock_quantity int          not null,
    sales_type     varchar(20)  not null,
    description    text         null,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (id),
    constraint fk_products_seller_id foreign key (seller_id) references users (id),
    constraint ck_products_stock_quantity check (stock_quantity >= 0)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
