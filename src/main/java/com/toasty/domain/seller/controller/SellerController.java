package com.toasty.domain.seller.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.seller.controller.dto.request.ShopImageUploadUrlRequest;
import com.toasty.domain.seller.controller.dto.response.ShopImageUploadUrlResponse;
import com.toasty.domain.seller.service.SellerShopImageService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller", description = "판매자 API")
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerShopImageService sellerShopImageService;

    @Operation(
            summary = "샵 이미지 업로드 주소 발급",
            description =
                    """
                    셀러가 샵 이미지를 S3에 직접 올릴 주소를 발급받습니다. 유저가 사진을 고른 직후 한 번 요청하세요.
                    사진은 서버를 거치지 않고 uploadUrl로 바로 PUT 하며, 발급 때 선언한 형식과 크기를 그대로 보내야 합니다.
                    업로드가 끝나면 imageUrl로 사진을 읽을 수 있고, 그 값을 판매자 온보딩 제출에 그대로 넣습니다.
                    아직 역할이 없는 유저도 호출할 수 있어 온보딩 화면에서 바로 쓸 수 있습니다.
                    """)
    @LoginRequired
    @PostMapping("/shop-image/upload-url")
    public ApiResponse<ShopImageUploadUrlResponse> issueShopImageUploadUrl(
            @Valid @RequestBody ShopImageUploadUrlRequest request, @LoginUser AuthUser user) {
        return ApiResponse.ok(
                sellerShopImageService.issueUploadUrl(request.toCommand(user.userId())));
    }
}
