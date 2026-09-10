package com.toasty.domain.seller.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.seller.controller.dto.request.ShopImageUploadUrlRequest;
import com.toasty.domain.seller.controller.dto.response.ShopImageUploadUrlResponse;
import com.toasty.domain.seller.controller.dto.response.ShopNameSearchResponse;
import com.toasty.domain.seller.controller.dto.response.ShopNameSuggestionResponse;
import com.toasty.domain.seller.service.SellerService;
import com.toasty.domain.seller.service.SellerShopImageService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller", description = "판매자 API")
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;
    private final SellerShopImageService sellerShopImageService;

    @Operation(
            summary = "스토어 이름 중복 조회",
            description =
                    """
                    입력한 스토어 이름을 다른 판매자가 쓰고 있는지 확인합니다.
                    duplicated가 true면 사용할 수 없는 이름입니다.
                    구매자의 닉네임과는 이름 공간이 달라, 같은 값을 쓰는 구매자가 있어도 중복으로 보지 않습니다.
                    토큰을 함께 보내면 자기 스토어 이름은 중복으로 보지 않습니다.
                    """)
    @GetMapping("/shop-name")
    public ApiResponse<ShopNameSearchResponse> searchShopName(
            @Parameter(description = "조회할 스토어 이름", required = true, example = "토스티상회")
                    @RequestParam
                    @NotBlank(message = "스토어 이름은 필수입니다.") @Size(max = 20, message = "스토어 이름은 20자를 넘을 수 없습니다.") String shopName,
            @LoginUser AuthUser user) {
        return ApiResponse.ok(
                sellerService.searchShopName(shopName, user == null ? null : user.sellerId()));
    }

    @Operation(
            summary = "추천 스토어 이름 발급",
            description =
                    """
                    온보딩 화면의 스토어 이름 입력창에 채워 둘 값을 받습니다. 유저가 그대로 써도 되고 지우고 새로 입력해도 됩니다.
                    스토어 이름은 구매자에게 그대로 노출되는 간판이라, 유저가 직접 정하도록 유도하고 이 값은 예시로만 쓰세요.
                    발급만 하고 자리를 잡아두지는 않아, 제출 전에 다른 판매자가 먼저 쓸 수 있습니다.
                    그 경우 온보딩 제출이 SELLER_SHOP_NAME_DUPLICATED로 실패하므로 다시 발급받게 하세요.
                    """)
    @LoginRequired
    @GetMapping("/shop-name/suggestion")
    public ApiResponse<ShopNameSuggestionResponse> suggestShopName() {
        return ApiResponse.ok(sellerService.suggestShopName());
    }

    @Operation(
            summary = "샵 이미지 업로드 주소 발급",
            description =
                    """
                    셀러가 샵 이미지를 S3에 직접 올릴 주소를 발급받습니다. 유저가 사진을 고른 직후 한 번 요청하세요.
                    사진은 서버를 거치지 않고 uploadUrl로 바로 PUT 하며, 발급 때 선언한 형식과 크기를 그대로 보내야 합니다.
                    업로드가 끝나면 objectKey를 들고 있다가 판매자 온보딩 제출에 넣습니다. 사진을 읽을 주소는 서버가 만듭니다.
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
