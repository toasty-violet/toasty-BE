package com.toasty.domain.order.controller.dto.response;

import com.toasty.domain.order.entity.Courier;
import io.swagger.v3.oas.annotations.media.Schema;

/** 택배사 드롭다운 한 줄. */
public record CourierResponse(
        @Schema(description = "운송장 등록에 넣을 값", example = "CJ_LOGISTICS") String code,
        @Schema(description = "화면에 보여줄 이름", example = "CJ 대한통운") String name) {

    public static CourierResponse from(Courier courier) {
        return new CourierResponse(courier.name(), courier.displayName());
    }
}
