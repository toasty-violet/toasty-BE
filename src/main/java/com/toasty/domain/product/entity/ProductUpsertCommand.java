package com.toasty.domain.product.entity;

/** productId가 null이면 새로 등록할 상품이다. imageObjectKey가 null이면 기존 사진을 그대로 둔다. */
public record ProductUpsertCommand(
        Long productId,
        String name,
        int price,
        int stockQuantity,
        String description,
        String imageObjectKey) {

    public boolean isNew() {
        return productId == null;
    }
}
