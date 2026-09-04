package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 송출정보는 담지 않는다. 송출 직전에 재발급 API로 받는다. */
public record LiveCreateResponse(
        @Schema(description = "생성된 라이브 정보") LiveDetailResponse live,
        @Schema(description = "함께 등록된 상품. 순서가 라이브 내 노출 순서다") List<LiveProductResponse> products) {

    public static LiveCreateResponse of(Live live, List<LiveProductResponse> products) {
        return new LiveCreateResponse(LiveDetailResponse.from(live), products);
    }
}
