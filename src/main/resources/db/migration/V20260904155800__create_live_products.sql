create table live_products
(
    id            bigint      not null auto_increment,
    live_id       bigint      not null,
    product_id    bigint      not null,
    status        varchar(20) not null,
    display_order int         not null default 0,
    created_at    datetime(6) not null,
    updated_at    datetime(6) not null,
    primary key (id),
    unique key uk_live_products_live_id_product_id (live_id, product_id),
    constraint fk_live_products_live_id foreign key (live_id) references lives (id),
    constraint fk_live_products_product_id foreign key (product_id) references products (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
