-- 셀러 라이브탭이 seller_id + status로 자기 라이브를 거른다.
-- 기존 idx_lives_seller_id는 이 인덱스의 왼쪽 접두사라 중복이므로 함께 지운다.
create index idx_lives_seller_id_status on lives (seller_id, status);

drop index idx_lives_seller_id on lives;
