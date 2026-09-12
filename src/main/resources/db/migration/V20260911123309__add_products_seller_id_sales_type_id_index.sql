-- 스토어 화면은 한 셀러의 판매중 상품만, 셀러 상품탭의 상태 칩은 그 상태 하나만 최신순으로 읽는다.
-- 상태가 앞에 서야 걸러내기와 정렬, 커서를 인덱스 하나로 끝낸다.
-- 전체 칩은 상태를 등호로 묶지 않아(다 팔린 것만 제외) 이 인덱스를 타지 않고 (seller_id, id)로 간다.
create index idx_products_seller_id_sales_type_id on products (seller_id, sales_type, id);
