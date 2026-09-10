package com.toasty.domain.product.entity;

import java.util.List;

/** 셀러 상품탭에서 상품 하나를 고친다. imageObjectKeys는 고치고 난 뒤의 사진 전체를 노출 순서대로 담는다. */
public record SellerProductUpdateCommand(
        Long productId,
        Long sellerId,
        String name,
        int price,
        int stockQuantity,
        String description,
        List<String> imageObjectKeys) {}
