-- 라이브 시청 화면에서 나온 주문을 방송별로 집계하기 위해 어느 방송에서 산 주문인지 남긴다.
-- 스토어나 상품 상세에서 산 주문은 비어 있다.
alter table orders
    add column live_id bigint null after seller_id,
    add key idx_orders_live_id_seller_id (live_id, seller_id);
