-- viewer_count는 지금 보고 있는 사람 수라 방송이 끝나면 0으로 돌아간다.
-- 지난 방송 집계에 쓸 최고 동시 시청자 수를 따로 남긴다.
alter table lives
    add column peak_viewer_count int not null default 0 after viewer_count;
