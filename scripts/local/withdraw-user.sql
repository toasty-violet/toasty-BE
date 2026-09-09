-- 로컬에서 탈퇴 이후 흐름을 테스트하기 위해 유저를 탈퇴 처리한다.
-- 탈퇴 API와 같은 상태를 만든다. 카카오 연결 끊기는 하지 않는다.
-- Flyway 마이그레이션이 아니므로 db/migration 으로 옮기지 않는다.
--
-- 사용법: @kakao_id 에 본인 계정 값을 채우고 IntelliJ Query Console 에서 실행한다.
--   select id, kakao_id, role, nickname, deleted_at from users;
--
-- 제약
--   - 구매자·판매자 행은 거래 상대방 식별에 쓰이므로 남긴다. 배송지만 지운다.
--   - 결제 기록(payments)도 남는다.
--   - 탈퇴하면 kakao_id 가 바뀌므로, 같은 계정으로 다시 로그인하면 새 유저로 가입된다.
--   - 이미 탈퇴한 유저에게 다시 실행하면 조회되지 않아 아무 일도 일어나지 않는다.

set @kakao_id = '여기에_본인_kakao_id';

-- users.kakao_id 는 utf8mb4_unicode_ci 인데 커넥션 기본값은 utf8mb4_0900_ai_ci 라
-- 사용자 변수와 그냥 비교하면 collation 충돌(1267)이 난다.
set @user_id = (select id
                from users
                where kakao_id = @kakao_id collate utf8mb4_unicode_ci
                  and deleted_at is null);

delete
from addresses
where customer_id in (select id from customers where user_id = @user_id);

-- kakao_id 와 nickname 은 unique 라 값을 그대로 두면 같은 계정으로 재가입하거나 그 닉네임을 다시 쓸 수 없다.
-- 탈퇴 API 와 같은 규칙(withdrawn_{id}, del_{12자리})으로 자리를 비운다.
update users
set deleted_at = now(6),
    kakao_id   = concat('withdrawn_', id),
    nickname   = concat('del_', substring(replace(uuid(), '-', ''), 1, 12))
where id = @user_id;
