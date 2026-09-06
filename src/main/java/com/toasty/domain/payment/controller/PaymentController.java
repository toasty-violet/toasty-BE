package com.toasty.domain.payment.controller;

import com.toasty.domain.auth.annotation.LoginRequired;
import com.toasty.domain.auth.annotation.LoginUser;
import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.payment.controller.dto.response.PayerIdSessionResponse;
import com.toasty.domain.payment.service.PaymentService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "payerId 확보용 결제 세션 생성",
            description =
                    "유저의 point3 결제자 식별값(payerId)을 받기 위해 100원짜리 결제 세션을 만듭니다. 금액과 상품명은 서버가 고정하므로"
                            + " 요청값이 없습니다. 응답의 sessionId로 결제창을 열면 결제 완료 후 payerId를 받을 수 있습니다.")
    @LoginRequired
    @GetMapping("/payer-id")
    public ApiResponse<PayerIdSessionResponse> createPayerIdSession(@LoginUser AuthUser user) {
        return ApiResponse.ok(paymentService.createPayerIdSession(user.userId()));
    }
}
