create table product_images
(
    id            bigint       not null auto_increment,
    product_id    bigint       not null,
    image_url     varchar(500) not null,
    display_order int          not null default 0,
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    primary key (id),
    -- display_order = 0이 대표 이미지다. 한 상품에 대표가 둘일 수 없다.
    unique key uk_product_images_product_id_display_order (product_id, display_order),
    constraint fk_product_images_product_id foreign key (product_id) references products (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
