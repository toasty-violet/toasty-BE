-- 홈 화면이 방송 중을 시청자 많은 순으로, 방송 예정을 임박한 순으로 읽는다.
create index idx_lives_status_viewer_count on lives (status, viewer_count);

create index idx_lives_status_scheduled_at on lives (status, scheduled_at);
