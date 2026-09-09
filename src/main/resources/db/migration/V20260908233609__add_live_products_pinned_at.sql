-- 셀러가 고정한 시각. 한 번 고정된 상품은 계속 구매할 수 있고(status = ACTIVE),
-- 그중 가장 최근에 고정된 것이 방송 화면에 "현재 고정 상품"으로 뜬다.
alter table live_products
    add column pinned_at datetime(6) null;
