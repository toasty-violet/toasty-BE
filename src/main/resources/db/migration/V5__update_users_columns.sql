alter table users
    change column user_type role varchar(20) null,
    drop column postal_code,
    drop column address,
    drop column payment_linked,
    add column payer_id varchar(50) null after nickname,
    add constraint uk_users_nickname unique (nickname);
