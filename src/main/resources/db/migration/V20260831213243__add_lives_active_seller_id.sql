-- active_seller_id: LIVE 상태일 때만 seller_id가 들어간다. MySQL은 부분 unique 인덱스를 지원하지
-- 않지만 unique 인덱스가 NULL 중복은 허용하므로, 이 컬럼으로 셀러당 동시 LIVE 1개를 강제한다.
alter table lives
    add column active_seller_id bigint null after playback_url,
    add constraint uk_lives_active_seller_id unique (active_seller_id);
