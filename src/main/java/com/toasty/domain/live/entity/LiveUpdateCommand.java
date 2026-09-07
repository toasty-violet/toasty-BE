package com.toasty.domain.live.entity;

import com.toasty.domain.product.entity.ProductUpsertCommand;
import java.time.LocalDateTime;
import java.util.List;

/** 보내지 않은 필드는 null이고 수정하지 않는다. products는 null이 아니면 전체 교체다. */
public record LiveUpdateCommand(
        Long liveId,
        Long sellerId,
        String title,
        String description,
        LocalDateTime scheduledAt,
        List<ProductUpsertCommand> products) {}
