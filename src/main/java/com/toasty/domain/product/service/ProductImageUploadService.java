package com.toasty.domain.product.service;

import com.toasty.domain.product.controller.dto.response.ProductImageUploadUrlResponse;
import com.toasty.domain.product.entity.ProductImageUploadCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** 셀러가 상품 사진을 서버를 거치지 않고 S3에 직접 올릴 수 있도록 업로드 주소를 발급한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /** 셀러가 고른 사진 장수만큼 업로드 주소를 만들어 준다. */
    public ProductImageUploadUrlResponse issueUploadUrls(ProductImageUploadCommand command) {
        List<ProductImageUploadUrlResponse.Upload> uploads =
                command.files().stream().map(file -> presign(command.sellerId(), file)).toList();
        return new ProductImageUploadUrlResponse(uploads);
    }

    private ProductImageUploadUrlResponse.Upload presign(
            Long sellerId, ProductImageUploadCommand.File file) {
        String objectKey = generateObjectKey(sellerId, file.contentType());
        // contentType과 contentLength를 서명에 포함시켜, 선언한 것과 다른 파일을 올리면 S3가 거부하게 한다.
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(objectKey)
                        .contentType(file.contentType())
                        .contentLength(file.contentLength())
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(
                                Duration.ofSeconds(s3Properties.presignedUrlExpirySeconds()))
                        .putObjectRequest(putObjectRequest)
                        .build();

        try {
            String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
            return new ProductImageUploadUrlResponse.Upload(
                    objectKey, uploadUrl, s3Properties.presignedUrlExpirySeconds());
        } catch (SdkException e) {
            log.error("상품 사진 업로드 주소 발급 실패. objectKey={}", objectKey, e);
            throw new CustomException(ProductErrorCode.PRODUCT_UPLOAD_URL_ISSUE_FAILED, e);
        }
    }

    // 셀러 번호를 넣어 라이브가 저장되지 않고 남은 사진이 누구 것인지 추적할 수 있게 한다.
    private String generateObjectKey(Long sellerId, String contentType) {
        return s3Properties.pendingPrefix()
                + sellerId
                + "/"
                + LocalDate.now().format(DATE_PATH)
                + "/"
                + UUID.randomUUID()
                + "."
                + EXTENSIONS.get(contentType);
    }
}
