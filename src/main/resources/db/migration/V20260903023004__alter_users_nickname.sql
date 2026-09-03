-- 온보딩 전이라 닉네임이 비어 있던 유저에게 not null 적용 전 임시 닉네임을 채운다
update users
set nickname = concat('user_', lpad(id, 12, '0'))
where nickname is null;

alter table users
    modify column nickname varchar(20) not null;
