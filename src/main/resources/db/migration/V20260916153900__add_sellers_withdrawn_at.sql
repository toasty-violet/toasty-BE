-- 탈퇴한 판매자는 상점명만 바뀌고 행은 남아, 목록 화면이 탈퇴한 스토어를 집어 올렸다.
-- 탈퇴 시각을 남겨 추천·인기 목록에서 걸러낼 수 있게 한다.
alter table sellers
    add column withdrawn_at datetime(6) null;

-- 이미 탈퇴한 판매자는 상점명 접두어로만 알 수 있어, 그 값으로 한 번 채운다.
update sellers
set withdrawn_at = now(6)
where shop_name like 'del\_%';
