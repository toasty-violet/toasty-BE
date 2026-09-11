-- 로컬에서 온보딩을 다시 테스트하기 위해 유저를 온보딩 이전 상태로 되돌린다.
-- Flyway 마이그레이션이 아니므로 db/migration 으로 옮기지 않는다.
--
-- 사용법: @kakao_id 에 본인 계정 값을 채우고 IntelliJ Query Console 에서 실행한다.
--   select id, kakao_id, role from users;
--
-- 제약
--   - 판매자에게 상품(products)이나 라이브(lives)가 있으면 외래키 제약으로 실패한다. 해당 데이터를 먼저 지운다.
--   - 구매자의 배송지(addresses)는 cascade 로 함께 삭제된다.

set @kakao_id = '여기에_본인_kakao_id';

-- users.kakao_id 는 utf8mb4_unicode_ci 인데 커넥션 기본값은 utf8mb4_0900_ai_ci 라
-- 사용자 변수와 그냥 비교하면 collation 충돌(1267)이 난다.
delete
from sellers
where user_id = (select id from users where kakao_id = @kakao_id collate utf8mb4_unicode_ci);

delete
from customers
where user_id = (select id from users where kakao_id = @kakao_id collate utf8mb4_unicode_ci);

-- 닉네임과 상점명은 위에서 지운 행이 들고 있어, 유저에는 되돌릴 값이 역할뿐이다.
update users
set role = null
where kakao_id = @kakao_id collate utf8mb4_unicode_ci;
