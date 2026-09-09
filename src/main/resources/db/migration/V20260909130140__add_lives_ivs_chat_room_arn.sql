-- 라이브마다 IVS Chat 방을 하나 둔다. 메시지는 클라이언트가 AWS와 직접 주고받아 서버를 지나지 않는다.
-- 이미 만들어진 라이브에는 방이 없으므로 null을 허용한다.
alter table lives
    add column ivs_chat_room_arn varchar(200) null;
