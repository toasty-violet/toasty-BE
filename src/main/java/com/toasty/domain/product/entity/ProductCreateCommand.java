package com.toasty.domain.product.entity;

public record ProductCreateCommand(
        String name, int price, int stockQuantity, String description, String imageObjectKey) {}
