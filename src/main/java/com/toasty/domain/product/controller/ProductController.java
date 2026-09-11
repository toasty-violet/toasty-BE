package com.toasty.domain.product.controller;

import com.toasty.domain.product.controller.dto.response.ProductDetailResponse;
import com.toasty.domain.product.service.ProductService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product", description = "구매자가 보는 상품 API")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "상품 상세 조회",
            description =
                    "상품 상세 화면을 채웁니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다."
                            + " 셀러 카드는 응답의 sellerId로 스토어 정보를 따로 부르면 되고, 아래 붙는"
                            + " otherProducts에는 같은 스토어의 다른 상품이 최대 3개 담깁니다."
                            + " 지금 살 수 있는 상품만 열립니다. 라이브에서 팔 상품이나 다 팔린 상품은"
                            + " 링크를 직접 열어도 404입니다.")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.ok(productService.findProductDetail(productId));
    }
}
