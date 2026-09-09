package com.toasty.domain.user.controller.dto.request;

import com.toasty.domain.customer.controller.dto.request.AddressRequest;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerOnboardingRequest(
        @Schema(description = "이름", example = "김토스티")
                @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.") String name,
        @Schema(description = "닉네임", example = "토스티")
                @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 20, message = "닉네임은 20자를 넘을 수 없습니다.") String nickname,
        @Schema(description = "휴대폰 번호 — 하이픈 없이 숫자만", example = "01012345678")
                @NotBlank(message = "휴대폰 번호는 필수입니다.") @Pattern(regexp = "^01[016-9]\\d{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.") String phoneNumber,
        @Schema(
                        description = "계좌 등록 결제 세션 ID — 결제 세션 생성 API로 받아 결제창을 통과한 값",
                        example = "pymt_sess-019f0000-0000-7000-9000-000000000000")
                @NotBlank(message = "결제 세션 아이디는 필수입니다.") @Size(max = 100, message = "결제 세션 아이디는 100자를 넘을 수 없습니다.") String sessionId,
        @Schema(description = "기본 배송지") @NotNull(message = "주소는 필수입니다.") @Valid AddressRequest address) {

    public CustomerOnboardingCommand toCommand(Long userId) {
        return new CustomerOnboardingCommand(
                userId, name, nickname, phoneNumber, sessionId, address.toCommand());
    }
}
