-- 스토어 홈에 뜨는 배송비 정책과 판매 내역을 판매자 행에 둔다.
-- 배송비는 판매자가 나중에 수정하고, 판매 내역은 주문이 쌓일 때 서버가 갱신한다.
-- 온보딩에서는 받지 않아 모두 0으로 시작한다.

alter table sellers
    add column base_shipping_fee        int    not null default 0 after account_number,
    add column free_shipping_threshold  int    not null default 0 after base_shipping_fee,
    add column remote_area_shipping_fee int    not null default 0 after free_shipping_threshold,
    add column total_sales_count        int    not null default 0 after remote_area_shipping_fee,
    add column total_buyer_count        int    not null default 0 after total_sales_count,
    -- 누적 판매액은 원 단위 합계라 int 상한을 넘길 수 있다
    add column total_sales_amount       bigint not null default 0 after total_buyer_count;
