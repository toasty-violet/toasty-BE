-- payerId가 접두사 + UUID 형식이라 50자로는 모자랄 수 있어 늘린다.
alter table customers
    modify column payer_id varchar(100) null;
