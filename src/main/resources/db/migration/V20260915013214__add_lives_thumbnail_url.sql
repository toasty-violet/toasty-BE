-- 홈 라이브 카드의 배경으로 쓰는 썸네일 주소.
-- 방송 전에는 찍어둘 화면이 없어 비어 있을 수 있다.
alter table lives
    add column thumbnail_url varchar(500) null;
