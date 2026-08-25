package com.toasty.global.auth;

import com.toasty.global.exception.CommonErrorCode;
import com.toasty.global.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// TODO 인증 구현 후 JWT 기반 구현체로 교체한다.
// 헤더를 그대로 믿으므로 누구든 다른 셀러를 사칭할 수 있다. 운영에 배포하면 안 된다.
@Component
@RequiredArgsConstructor
public class HeaderSellerProvider implements SellerProvider {

    private static final String SELLER_ID_HEADER = "X-Seller-Id";

    private final HttpServletRequest request;

    @Override
    public Long currentSellerId() {
        String value = request.getHeader(SELLER_ID_HEADER);
        if (value == null || value.isBlank()) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
