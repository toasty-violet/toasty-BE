package com.toasty.domain.product.controller;

import com.toasty.domain.product.controller.dto.response.StoreProductsResponse;
import com.toasty.domain.product.entity.StoreProductPageCommand;
import com.toasty.domain.product.service.ProductService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "구매자가 보는 스토어 API")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreProductController {

    private final ProductService productService;

    @Operation(
            summary = "스토어 상품 목록 조회",
            description =
                    "스토어 화면의 상품 그리드를 채웁니다. 화면을 열 때 파라미터 없이 부르고, 목록 끝에 닿을 때마다"
                            + " 직전 응답의 nextCursor를 그대로 넘겨 이어 받으세요. hasNext가 false면 더 부르지"
                            + " 않습니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다. 지금 살 수 있는 상품만"
                            + " 내려주므로 라이브에서 팔 상품이나 다 팔린 상품은 담기지 않습니다.")
    @GetMapping("/{sellerId}/products")
    public ApiResponse<StoreProductsResponse> findStoreProducts(
            @Parameter(description = "스토어를 여는 셀러 번호") @PathVariable Long sellerId,
            @Parameter(description = "직전 응답의 nextCursor. 첫 요청에는 넣지 않는다")
                    @RequestParam(required = false)
                    Long cursor) {
        return ApiResponse.ok(
                productService.findStoreProducts(new StoreProductPageCommand(sellerId, cursor)));
    }
}
