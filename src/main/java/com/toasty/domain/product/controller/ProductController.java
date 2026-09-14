package com.toasty.domain.product.controller;

import com.toasty.domain.product.controller.dto.response.BestProductResponse;
import com.toasty.domain.product.controller.dto.response.ProductDetailResponse;
import com.toasty.domain.product.service.ProductService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
            summary = "홈 베스트 아이템 조회",
            description =
                    "홈 하단의 베스트 아이템을 채웁니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다."
                            + " 스토어와 상관없이 조회수가 높은 순으로 최대 10개를 내려주고, 조회수가 같으면"
                            + " 최근 등록한 상품이 앞섭니다. 지금 살 수 있는 상품만 담아 카드를 누르면 상품 상세가"
                            + " 열립니다. 목록이 짧아 페이지를 넘기지 않습니다.")
    @GetMapping("/best")
    public ApiResponse<List<BestProductResponse>> findBestProducts() {
        return ApiResponse.ok(productService.findBestProducts());
    }

    @Operation(
            summary = "상품 상세 조회",
            description =
                    "상품 상세 화면을 채웁니다. 인증이 필요 없어 비로그인 유저도 호출할 수 있습니다."
                            + " 셀러 카드는 응답의 sellerId로 스토어 정보를 따로 부르면 되고, 아래 붙는"
                            + " otherProducts에는 같은 스토어의 다른 상품이 최대 3개 담깁니다."
                            + " 지금 살 수 있는 상품만 열립니다. 라이브에서 팔 상품이나 다 팔린 상품은"
                            + " 링크를 직접 열어도 404입니다. 상세가 열릴 때마다 조회수가 1 올라 베스트 아이템 순서에"
                            + " 반영되므로, 화면을 한 번 열 때 한 번만 부르세요.")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.ok(productService.findProductDetail(productId));
    }
}
