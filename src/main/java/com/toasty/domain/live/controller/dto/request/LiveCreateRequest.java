package com.toasty.domain.live.controller.dto.request;

import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.product.entity.ProductCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record LiveCreateRequest(
        @Schema(description = "라이브 제목", example = "빈티지 신상 공개라방")
                @NotBlank(message = "제목은 필수입니다.") @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
        @Schema(description = "라이브 설명", example = "가을 신상 소개합니다")
                @Size(max = 1000, message = "설명은 1000자를 넘을 수 없습니다.") String description,
        @Schema(description = "방송 예정 시각. 이 시각에 자동으로 시작되지는 않습니다.", example = "2026-09-05T13:00:00")
                @NotNull(message = "방송 예정 시각은 필수입니다.") @Future(message = "방송 예정 시각은 현재 이후여야 합니다.") LocalDateTime scheduledAt,
        @Schema(description = "이번 방송에서 판매할 상품")
                @NotEmpty(message = "판매할 상품을 한 개 이상 등록해주세요.") @Size(max = 20, message = "상품은 한 번에 20개까지 등록할 수 있습니다.") List<@Valid @NotNull Product> products) {

    public LiveCreateCommand toCommand(Long sellerId) {
        return new LiveCreateCommand(
                sellerId,
                title,
                description,
                scheduledAt,
                products.stream().map(Product::toCommand).toList());
    }

    public record Product(
            @Schema(description = "상품명", example = "핸드메이드 가죽 벨트")
                    @NotBlank(message = "상품명은 필수입니다.") @Size(max = 200, message = "상품명은 200자를 넘을 수 없습니다.") String name,
            @Schema(description = "가격(원)", example = "45000")
                    @NotNull(message = "가격은 필수입니다.") @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.") Integer price,
            @Schema(description = "재고 수량", example = "1")
                    @NotNull(message = "재고 수량은 필수입니다.") @Positive(message = "재고 수량은 1개 이상이어야 합니다.") Integer stockQuantity,
            @Schema(description = "상품 상세 설명")
                    @Size(max = 2000, message = "상세 설명은 2000자를 넘을 수 없습니다.") String description,
            @Schema(description = "업로드 주소 발급에서 받은 objectKey") @NotBlank(message = "상품 사진은 필수입니다.") String imageObjectKey) {

        public ProductCreateCommand toCommand() {
            return new ProductCreateCommand(
                    name, price, stockQuantity, description, imageObjectKey);
        }
    }
}
