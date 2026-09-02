package com.toasty.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.toasty.domain.product.controller.dto.response.ProductImageUploadUrlResponse;
import com.toasty.domain.product.entity.ProductImageUploadCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 사진 업로드 주소 발급")
class ProductImageUploadServiceTest {

    private static final Long SELLER_ID = 7L;

    @Mock private S3Presigner s3Presigner;

    private final S3Properties s3Properties =
            new S3Properties("toasty-media", "products/pending/", 300, "https://cdn.example.com");

    private ProductImageUploadService service;

    private ProductImageUploadService service() {
        if (service == null) {
            service = new ProductImageUploadService(s3Presigner, s3Properties);
        }
        return service;
    }

    private void givenPresignSucceeds() throws Exception {
        URL url =
                URI.create("https://toasty-media.s3.ap-northeast-2.amazonaws.com/put?sig=x")
                        .toURL();
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        given(presigned.url()).willReturn(url);
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presigned);
    }

    private ProductImageUploadCommand command(String... contentTypes) {
        List<ProductImageUploadCommand.File> files =
                java.util.Arrays.stream(contentTypes)
                        .map(type -> new ProductImageUploadCommand.File(type, 1024L))
                        .toList();
        return new ProductImageUploadCommand(SELLER_ID, files);
    }

    @Test
    @DisplayName("요청한 장수만큼 발급하고 만료 시간은 설정값을 따른다")
    void 장수만큼_발급한다() throws Exception {
        givenPresignSucceeds();

        ProductImageUploadUrlResponse response =
                service().issueUploadUrls(command("image/jpeg", "image/png"));

        assertThat(response.uploads()).hasSize(2);
        assertThat(response.uploads())
                .allSatisfy(upload -> assertThat(upload.expiresIn()).isEqualTo(300));
    }

    @Test
    @DisplayName("객체 키에 셀러 번호와 날짜가 들어가고 형식에 맞는 확장자가 붙는다")
    void 객체_키_형식() throws Exception {
        givenPresignSucceeds();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        ProductImageUploadUrlResponse response =
                service().issueUploadUrls(command("image/jpeg", "image/png", "image/webp"));

        assertThat(response.uploads().get(0).objectKey())
                .startsWith("products/pending/7/" + today + "/")
                .endsWith(".jpg");
        assertThat(response.uploads().get(1).objectKey()).endsWith(".png");
        assertThat(response.uploads().get(2).objectKey()).endsWith(".webp");
    }

    @Test
    @DisplayName("발급할 때마다 객체 키가 달라 서로 덮어쓰지 않는다")
    void 객체_키가_겹치지_않는다() throws Exception {
        givenPresignSucceeds();

        ProductImageUploadUrlResponse response =
                service().issueUploadUrls(command("image/jpeg", "image/jpeg"));

        assertThat(response.uploads().get(0).objectKey())
                .isNotEqualTo(response.uploads().get(1).objectKey());
    }

    @Test
    @DisplayName("선언한 형식과 크기를 서명에 포함해 다른 파일을 올리지 못하게 한다")
    void 형식과_크기를_서명에_담는다() throws Exception {
        givenPresignSucceeds();
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        service()
                .issueUploadUrls(
                        new ProductImageUploadCommand(
                                SELLER_ID,
                                List.of(
                                        new ProductImageUploadCommand.File(
                                                "image/webp", 812304L))));

        org.mockito.Mockito.verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectRequest put = captor.getValue().putObjectRequest();
        assertThat(put.bucket()).isEqualTo("toasty-media");
        assertThat(put.contentType()).isEqualTo("image/webp");
        assertThat(put.contentLength()).isEqualTo(812304L);
    }

    @Test
    @DisplayName("발급이 실패하면 PRODUCT_UPLOAD_URL_ISSUE_FAILED다")
    void 발급_실패() {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willThrow(SdkClientException.create("presign failed"));

        assertThatThrownBy(() -> service().issueUploadUrls(command("image/jpeg")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_UPLOAD_URL_ISSUE_FAILED);
    }
}
