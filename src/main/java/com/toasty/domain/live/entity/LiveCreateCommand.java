package com.toasty.domain.live.entity;

import com.toasty.domain.product.entity.ProductCreateCommand;
import java.time.LocalDateTime;
import java.util.List;

public record LiveCreateCommand(
        Long sellerId,
        String title,
        String description,
        LocalDateTime scheduledAt,
        List<ProductCreateCommand> products) {}
