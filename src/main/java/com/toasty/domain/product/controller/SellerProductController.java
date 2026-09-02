package com.toasty.domain.product.controller;

import com.toasty.domain.product.controller.dto.request.ProductImageUploadUrlRequest;
import com.toasty.domain.product.controller.dto.response.ProductImageUploadUrlResponse;
import com.toasty.domain.product.service.ProductImageUploadService;
import com.toasty.global.auth.SellerProvider;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller Product", description = "셀러 상품 관리 API")
@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductImageUploadService productImageUploadService;
    private final SellerProvider sellerProvider;

    @Operation(
            summary = "상품 사진 업로드 주소 발급",
            description =
                    "셀러가 상품 사진을 S3에 직접 올릴 주소를 발급받습니다. 라이브 설정에서 사진을 고른 직후 장수만큼 한 번에"
                            + " 요청하세요. 사진은 서버를 거치지 않고 이 주소로 바로 PUT 하며, 발급 때 선언한 형식과 크기를"
                            + " 그대로 보내야 합니다. 업로드가 끝나면 응답의 objectKey를 들고 있다가 라이브 생성 요청에"
                            + " 넣습니다. 주소는 짧은 시간만 유효하고, 올린 뒤 라이브를 저장하지 않은 사진은 자동으로 정리됩니다.")
    @PostMapping("/images/upload-url")
    public ApiResponse<ProductImageUploadUrlResponse> issueImageUploadUrls(
            @Valid @RequestBody ProductImageUploadUrlRequest request) {
        Long sellerId = sellerProvider.currentSellerId();
        return ApiResponse.ok(
                productImageUploadService.issueUploadUrls(request.toCommand(sellerId)));
    }
}
