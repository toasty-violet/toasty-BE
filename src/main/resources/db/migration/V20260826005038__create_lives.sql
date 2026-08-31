create table lives
(
    id               bigint       not null auto_increment,
    seller_id        bigint       not null,
    title            varchar(100) not null,
    description      varchar(1000) null,
    status           varchar(20)  not null,
    public_id        varchar(36)  not null,
    ivs_channel_arn  varchar(200) not null,
    playback_url     varchar(500) not null,
    started_at       datetime(6)  null,
    ended_at         datetime(6)  null,
    created_at       datetime(6)  not null,
    updated_at       datetime(6)  not null,
    primary key (id),
    unique key uk_lives_public_id (public_id),
    key idx_lives_seller_id (seller_id),
    constraint fk_lives_seller_id foreign key (seller_id) references users (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
