-- 탈퇴를 유저 행 삭제가 아닌 deleted_at 기록으로 처리한다.
-- 주문 이력에서 거래 상대방을 계속 식별해야 하므로 구매자·판매자 정보는 탈퇴 후에도 남긴다.
-- 그래서 유저 삭제 시 함께 지우던 cascade를 걷어내, 실수로 유저 행을 지우면 삭제가 막히게 한다.

alter table users
    add column deleted_at datetime null comment '탈퇴 시각. null이면 이용 중인 유저';

alter table customers
    drop foreign key fk_customers_user_id;

alter table customers
    add constraint fk_customers_user_id foreign key (user_id) references users (id);

alter table sellers
    drop foreign key fk_sellers_user_id;

alter table sellers
    add constraint fk_sellers_user_id foreign key (user_id) references users (id);
