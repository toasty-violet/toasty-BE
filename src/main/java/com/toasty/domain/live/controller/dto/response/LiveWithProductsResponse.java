package com.toasty.domain.live.controller.dto.response;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 라이브와 편성 상품을 함께 준다. 생성 응답과 상세 조회가 같은 형태를 쓴다. */
// 송출정보는 담지 않는다. 송출 직전에 재발급 API로 받는다.
public record LiveWithProductsResponse(
        @Schema(description = "라이브 정보") LiveDetailResponse live,
        @Schema(description = "편성된 상품. 순서가 라이브 내 노출 순서다") List<LiveProductResponse> products) {

    public static LiveWithProductsResponse of(Live live, List<LiveProductResponse> products) {
        return new LiveWithProductsResponse(LiveDetailResponse.from(live), products);
    }
}
