package com.toasty.domain.product.entity;

public record ProductCreateCommand(
        Long sellerId, String name, int price, int stockQuantity, String description) {}
