package com.toasty.domain.product.controller;

import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.annotation.SellerOnly;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.product.controller.dto.request.ProductImageUploadUrlRequest;
import com.toasty.domain.product.controller.dto.response.ProductImageUploadUrlResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductsResponse;
import com.toasty.domain.product.entity.SellerProductFilter;
import com.toasty.domain.product.entity.SellerProductPageCommand;
import com.toasty.domain.product.service.ProductImageUploadService;
import com.toasty.domain.product.service.ProductService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller Product", description = "셀러 상품 관리 API")
@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductImageUploadService productImageUploadService;
    private final ProductService productService;

    @Operation(
            summary = "상품 사진 업로드 주소 발급",
            description =
                    "셀러가 상품 사진을 S3에 직접 올릴 주소를 발급받습니다. 라이브 설정에서 사진을 고른 직후 장수만큼 한 번에"
                            + " 요청하세요. 사진은 서버를 거치지 않고 이 주소로 바로 PUT 하며, 발급 때 선언한 형식과 크기를"
                            + " 그대로 보내야 합니다. 업로드가 끝나면 응답의 objectKey를 들고 있다가 라이브 생성 요청에"
                            + " 넣습니다. 주소는 짧은 시간만 유효하고, 올린 뒤 라이브를 저장하지 않은 사진은 자동으로 정리됩니다.")
    @SellerOnly
    @PostMapping("/images/upload-url")
    public ApiResponse<ProductImageUploadUrlResponse> issueImageUploadUrls(
            @Valid @RequestBody ProductImageUploadUrlRequest request, @LoginUser AuthUser seller) {
        return ApiResponse.ok(
                productImageUploadService.issueUploadUrls(request.toCommand(seller.sellerId())));
    }

    @Operation(
            summary = "셀러 상품 목록 조회",
            description =
                    "셀러 상품탭을 채웁니다. 화면을 열 때 파라미터 없이 부르고, 목록 끝에 닿을 때마다 직전 응답의"
                            + " nextCursor를 그대로 넘겨 이어 받으세요. hasNext가 false면 더 부르지 않습니다."
                            + " 상태 칩은 status로 거르며, 다 팔린 상품은 어느 값으로도 나오지 않습니다."
                            + " 칩에 붙는 건수(counts)는 스크롤 중에 바뀌지 않아 첫 요청에서만 내려주고 이어 받을"
                            + " 때는 null입니다. 검색 화면도 이 API를 keyword와 함께 부르면 되고, 그때 건수는"
                            + " 검색 결과 안에서 셉니다.")
    @SellerOnly
    @GetMapping
    public ApiResponse<SellerProductsResponse> findMyProducts(
            @Parameter(description = "상태 칩. 생략하면 전체") @RequestParam(defaultValue = "ALL")
                    SellerProductFilter status,
            @Parameter(description = "직전 응답의 nextCursor. 첫 요청에는 넣지 않는다")
                    @RequestParam(required = false)
                    Long cursor,
            @Parameter(description = "상품명 검색어. 넣지 않으면 전체") @RequestParam(required = false)
                    String keyword,
            @LoginUser AuthUser seller) {
        return ApiResponse.ok(
                productService.findSellerProducts(
                        new SellerProductPageCommand(seller.sellerId(), status, cursor, keyword)));
    }
}
