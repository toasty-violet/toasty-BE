-- 구매자 주문내역이 자기 주문을 최신순으로 커서를 옮겨가며 읽는다.
-- 외래키가 만든 customer_id 인덱스는 이 인덱스가 대신 받쳐 주면서 InnoDB가 걷어간다.
create index idx_orders_customer_id_id on orders (customer_id, id);
