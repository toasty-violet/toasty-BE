package com.toasty.domain.user.controller.dto.request;

import com.toasty.domain.seller.entity.Bank;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerOnboardingRequest(
        @Schema(description = "스토어 이름", example = "토스티상회")
                @NotBlank(message = "스토어 이름은 필수입니다.") @Size(max = 20, message = "스토어 이름은 20자를 넘을 수 없습니다.") String shopName,
        @Schema(description = "스토어 소개글", example = "매일 아침 구운 빵을 라이브로 소개합니다.")
                @NotBlank(message = "스토어 소개글은 필수입니다.") @Size(max = 500, message = "스토어 소개글은 500자를 넘을 수 없습니다.") String description,
        @Schema(
                        description = "샵 이미지 업로드 주소 발급 때 받은 objectKey",
                        example =
                                "sellers/images/7/2026/09/07/3f2b8c19-0d4a-4c5e-9a71-2b6d0f8e1c33.jpg")
                @NotBlank(message = "스토어 이미지는 필수입니다.") String shopImageObjectKey,
        @Schema(description = "대표자 이름", example = "김토스티")
                @NotBlank(message = "대표자 이름은 필수입니다.") @Size(max = 50, message = "대표자 이름은 50자를 넘을 수 없습니다.") String sellerName,
        @Schema(description = "대표자 연락처 — 하이픈 없이 숫자만", example = "01012345678")
                @NotBlank(message = "대표자 연락처는 필수입니다.") @Pattern(regexp = "^01[016-9]\\d{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.") String phoneNumber,
        @Schema(description = "사업자등록번호 — 하이픈 없이 10자리, 선택 입력", example = "1234567890")
                @Pattern(regexp = "^\\d{10}$", message = "사업자등록번호는 하이픈 없이 10자리 숫자여야 합니다.") String businessNumber,
        @Schema(description = "정산 계좌 은행", example = "KAKAO_BANK") @NotNull(message = "은행은 필수입니다.") Bank bank,
        @Schema(description = "정산 계좌번호 — 하이픈 없이 숫자만", example = "333012345678")
                @NotBlank(message = "계좌번호는 필수입니다.") @Pattern(regexp = "^\\d{1,30}$", message = "계좌번호는 하이픈 없이 숫자만 30자리까지 입력할 수 있습니다.") String accountNumber) {

    // 선택 입력값인 사업자등록번호가 빈 문자열로 들어오면 미입력으로 취급한다.
    public SellerOnboardingRequest {
        if (businessNumber != null && businessNumber.isBlank()) {
            businessNumber = null;
        }
    }

    public SellerOnboardingCommand toCommand(Long userId) {
        return new SellerOnboardingCommand(
                userId,
                shopName,
                description,
                shopImageObjectKey,
                sellerName,
                phoneNumber,
                businessNumber,
                bank,
                accountNumber);
    }
}
