-- 홈 베스트 아이템이 조회수 순으로 판매중 상품을 고른다. 상품 상세를 열 때마다 1씩 오른다.
alter table products
    add column view_count int not null default 0 after description;

-- 판매중 안에서 조회수가 높은 순, 같으면 최신순으로 앞부분만 읽는다.
create index idx_products_sales_type_view_count_id on products (sales_type, view_count, id);
