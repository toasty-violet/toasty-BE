-- 도메인 구조 예시용 테이블. 실제 기능을 시작하면 새 버전으로 drop하고 domain/sample도 지운다.
create table samples
(
    id         bigint       not null auto_increment,
    title      varchar(100) not null,
    content    varchar(1000) null,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci;
