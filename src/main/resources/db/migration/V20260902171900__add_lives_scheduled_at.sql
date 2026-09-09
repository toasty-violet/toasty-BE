alter table lives
    add column scheduled_at datetime(6) null after description;

update lives
set scheduled_at = created_at
where scheduled_at is null;

alter table lives
    modify column scheduled_at datetime(6) not null;
