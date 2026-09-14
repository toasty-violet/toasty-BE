package com.toasty.domain.seller.service;

import com.toasty.domain.seller.controller.dto.response.ShopImageUploadUrlResponse;
import com.toasty.domain.seller.entity.ShopImageUploadCommand;
import com.toasty.domain.seller.exception.SellerErrorCode;
import com.toasty.global.config.SellerS3Properties;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
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
    private final S3Client s3Client;
    private final SellerS3Properties s3Properties;

    /** 샵 이미지를 올릴 주소와, 그 사진이 저장될 위치를 만들어 준다. */
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
                    objectKey, uploadUrl, s3Properties.presignedUrlExpirySeconds());
        } catch (SdkException e) {
            log.error("샵 이미지 업로드 주소 발급 실패. objectKey={}", objectKey, e);
            throw new CustomException(SellerErrorCode.SELLER_UPLOAD_URL_ISSUE_FAILED, e);
        }
    }

    /** 샵 이미지를 바꾼 뒤 더 이상 쓰지 않는 사진을 치운다. */
    // 지우기 실패가 이미 끝난 수정을 되돌리지 않게 한다. 남은 객체는 로그로 추적한다.
    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(objectKey)
                            .build());
        } catch (SdkException e) {
            log.error("샵 이미지 정리 실패. 버킷에 고아 객체가 남았다 - objectKey={}", objectKey, e);
        }
    }

    // 온보딩 제출 전에도 발급하므로 셀러 번호가 아직 없다. 유저 번호를 넣어, 온보딩이 제출받은 키가 누구 것인지 가려낼 수 있게 한다.
    private String generateObjectKey(Long userId, String contentType) {
        return s3Properties.imagePrefix()
                + userId
                + "/"
                + LocalDate.now().format(DATE_PATH)
                + "/"
                + UUID.randomUUID()
                + "."
                + EXTENSIONS.get(contentType);
    }
}
