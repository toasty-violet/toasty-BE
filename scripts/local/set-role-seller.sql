-- 로컬에서 지정한 유저를 초기화한 뒤 온보딩을 마친 판매자로 만든다.
-- Flyway 마이그레이션이 아니므로 db/migration 으로 옮기지 않는다.
--
-- 사용법: 아래 변수를 채우고 IntelliJ Query Console 에서 파일 전체를 실행한다.
--   select u.id, u.kakao_id, u.role, s.shop_name from users u left join sellers s on s.user_id = u.id;
--
-- 제약
--   - shop_name 은 sellers 전체에서 unique 하다. 다른 판매자가 쓰는 값이면 실패한다.
--     구매자의 닉네임과는 이름 공간이 달라 겹쳐도 된다.
--   - 이미 판매자였고 상품(products)이나 라이브(lives)가 남아 있으면 외래키 제약으로 실패한다.
--     그 경우 해당 상품·라이브를 먼저 지운다.

-- 대상 유저. users.id 를 이미 알고 있으면 아래 두 줄 대신 set @user_id = 1; 만 써도 된다.
-- kakao_id 는 varchar 라 반드시 따옴표로 감싼다.
set @kakao_id = '여기에_본인_kakao_id';
set @user_id = (select id from users where kakao_id = @kakao_id collate utf8mb4_unicode_ci);

-- 판매자 정보. 사업자등록번호는 unique 라 값을 넣으려면 겹치지 않는 10자리를 쓴다.
set @shop_name = '테스트상점';
set @seller_name = '김사장';
set @phone_number = '01012345678';
set @business_number = null;
set @bank = null;
set @account_number = null;

-- null 이면 유저를 못 찾은 것이다. 아래 문장들이 조용히 0건 처리되므로 여기서 멈추고 kakao_id 를 확인한다.
select @user_id as target_user_id;

-- 온보딩 이전 상태로 되돌린다.
delete
from sellers
where user_id = @user_id;

delete
from customers
where user_id = @user_id;

-- 판매자로 확정한다. 상점명은 users 가 아니라 sellers 가 가진다.
update users
set role = 'SELLER'
where id = @user_id;

insert into sellers (user_id, shop_name, shop_image_object_key, description, seller_name,
                     phone_number, business_number, bank, account_number, created_at, updated_at)
values (@user_id, @shop_name, null, null, @seller_name,
        @phone_number, @business_number, @bank, @account_number, now(6), now(6));

select u.id, u.kakao_id, u.role, s.shop_name, s.seller_name, s.phone_number, s.business_number
from users u
         join sellers s on s.user_id = u.id
where u.id = @user_id;
