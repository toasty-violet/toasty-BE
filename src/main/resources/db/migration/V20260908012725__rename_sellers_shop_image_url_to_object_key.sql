-- 샵 이미지는 버킷 주소를 조합한 URL 대신 objectKey를 저장한다.
alter table sellers
    change column shop_image_url shop_image_object_key varchar(500) null;
