-- 로컬에서 지정한 유저를 초기화한 뒤 온보딩을 마친 구매자로 만든다.
-- Flyway 마이그레이션이 아니므로 db/migration 으로 옮기지 않는다.
--
-- 사용법: 아래 변수를 채우고 IntelliJ Query Console 에서 파일 전체를 실행한다.
--   select id, kakao_id, role, nickname from users;
--
-- 제약
--   - nickname 은 users 전체에서 unique 하다. 다른 유저가 쓰는 값이면 실패한다.
--   - 기존에 배송지(addresses)가 있었다면 cascade 로 함께 삭제된다.

-- 대상 유저. users.id 를 이미 알고 있으면 아래 두 줄 대신 set @user_id = 1; 만 써도 된다.
-- kakao_id 는 varchar 라 반드시 따옴표로 감싼다.
set @kakao_id = '여기에_본인_kakao_id';
set @user_id = (select id from users where kakao_id = @kakao_id collate utf8mb4_unicode_ci);

-- 구매자 정보
set @nickname = '테스트구매자';
set @name = '김테스트';
set @phone_number = '01012345678';

-- null 이면 유저를 못 찾은 것이다. 아래 문장들이 조용히 0건 처리되므로 여기서 멈추고 kakao_id 를 확인한다.
select @user_id as target_user_id;

-- 온보딩 이전 상태로 되돌린다.
delete
from sellers
where user_id = @user_id;

delete
from customers
where user_id = @user_id;

-- 구매자로 확정한다. 상점명이 아닌 닉네임이 users.nickname 에 들어간다.
update users
set role     = 'CUSTOMER',
    nickname = @nickname
where id = @user_id;

insert into customers (user_id, name, phone_number, payer_id, created_at, updated_at)
values (@user_id, @name, @phone_number, null, now(6), now(6));

select u.id, u.kakao_id, u.role, u.nickname, c.name, c.phone_number
from users u
         join customers c on c.user_id = u.id
where u.id = @user_id;
