-- 셀러 상품탭이 자기 상품을 최신순으로 커서를 옮겨가며 읽는다.
-- seller_id만 있는 인덱스로는 커서 조건이 붙는 순간 index_merge와 filesort로 떨어진다.
-- 외래키가 자동으로 만든 seller_id 인덱스는 이 인덱스가 대신 받쳐 주면서 InnoDB가 걷어간다.
create index idx_products_seller_id_id on products (seller_id, id);
