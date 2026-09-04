package com.toasty.domain.seller.service;

import com.toasty.domain.seller.controller.dto.response.ShopImageUploadUrlResponse;
import com.toasty.domain.seller.entity.ShopImageUploadCommand;
import com.toasty.domain.seller.exception.SellerErrorCode;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** 셀러가 샵 이미지를 서버를 거치지 않고 S3에 직접 올릴 수 있도록 업로드 주소를 발급한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerShopImageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /** 샵 이미지를 올릴 주소와, 업로드에 성공했을 때 그 사진을 읽을 주소를 함께 만들어 준다. */
    public ShopImageUploadUrlResponse issueUploadUrl(ShopImageUploadCommand command) {
        String objectKey = generateObjectKey(command.userId(), command.contentType());
        // contentType과 contentLength를 서명에 포함시켜, 선언한 것과 다른 파일을 올리면 S3가 거부하게 한다.
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(objectKey)
                        .contentType(command.contentType())
                        .contentLength(command.contentLength())
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(
                                Duration.ofSeconds(s3Properties.presignedUrlExpirySeconds()))
                        .putObjectRequest(putObjectRequest)
                        .build();

        try {
            String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
            return new ShopImageUploadUrlResponse(
                    objectKey,
                    uploadUrl,
                    toImageUrl(objectKey),
                    s3Properties.presignedUrlExpirySeconds());
        } catch (SdkException e) {
            log.error("샵 이미지 업로드 주소 발급 실패. objectKey={}", objectKey, e);
            throw new CustomException(SellerErrorCode.SELLER_UPLOAD_URL_ISSUE_FAILED, e);
        }
    }

    // 온보딩 제출 전에도 발급하므로 셀러 번호가 아직 없다. 유저 번호를 넣어 남은 사진이 누구 것인지 추적할 수 있게 한다.
    private String generateObjectKey(Long userId, String contentType) {
        return s3Properties.sellerImagePrefix()
                + userId
                + "/"
                + LocalDate.now().format(DATE_PATH)
                + "/"
                + UUID.randomUUID()
                + "."
                + EXTENSIONS.get(contentType);
    }

    private String toImageUrl(String objectKey) {
        return s3Properties.publicBaseUrl() + "/" + objectKey;
    }
}
