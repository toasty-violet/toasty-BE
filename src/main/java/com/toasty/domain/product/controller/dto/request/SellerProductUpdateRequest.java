package com.toasty.domain.product.controller.dto.request;

import com.toasty.domain.product.entity.SellerProductUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SellerProductUpdateRequest(
        @Schema(description = "상품명")
                @NotBlank(message = "상품명은 필수입니다.") @Size(max = 200, message = "상품명은 200자를 넘을 수 없습니다.") String name,
        @Schema(description = "가격(원)", example = "29000")
                @NotNull(message = "가격은 필수입니다.") @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.") Integer price,
        @Schema(description = "재고 수량", example = "1")
                @NotNull(message = "재고 수량은 필수입니다.") @Positive(message = "재고 수량은 1개 이상이어야 합니다.") Integer stockQuantity,
        @Schema(description = "상세 설명") @Size(max = 2000, message = "상세 설명은 2000자를 넘을 수 없습니다.") String description,
        @Schema(description = "고치고 난 뒤의 사진 전체를 노출 순서대로. 첫 장이 대표 사진이 된다")
                @NotEmpty(message = "상품 사진은 한 장 이상이어야 합니다.") @Size(max = 5, message = "상품 사진은 5장까지 올릴 수 있습니다.") List<@NotBlank(message = "사진 정보는 비어 있을 수 없습니다.") String> imageObjectKeys) {

    public SellerProductUpdateCommand toCommand(Long productId, Long sellerId) {
        return new SellerProductUpdateCommand(
                productId, sellerId, name, price, stockQuantity, description, imageObjectKeys);
    }
}
