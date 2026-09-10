-- 배치가 IVS에서 읽어 적어두는 시청자 수. 요청이 올 때마다 IVS를 부르지 않기 위해서다.
-- 방송 중이 아닌 라이브는 0이다.
alter table lives
    add column viewer_count int not null default 0;
