package com.todo.global.service;

import com.todo.global.config.MinioProperties;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private FileService fileService;
    private MinioProperties props;

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    @BeforeEach
    void setUp() {
        props = new MinioProperties();
        props.setBucket("uploads");
        props.setPresignedUrlExpiration(3600);
        props.setPutPresignedUrlExpiration(600);
        fileService = new FileService(s3Client, s3Presigner, props);
    }

    @Test
    void 프로필_업로드용_presigned_put_url을_생성한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/upload", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", null)
        );

        assertThat(response.uploadUrl()).isEqualTo("https://storage.example.com/upload");
        assertThat(response.objectKey()).startsWith("profiles/1/");
        assertThat(response.objectKey()).endsWith(".png");

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(s3Presigner).should().presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration().getSeconds()).isEqualTo(600);
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("uploads");
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(captor.getValue().putObjectRequest().contentLength()).isNull();
    }

    @Test
    void 파일_크기를_전달하면_contentLength까지_서명한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/upload", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", 1024L)
        );

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(s3Presigner).should().presignPutObject(captor.capture());
        assertThat(captor.getValue().putObjectRequest().contentLength()).isEqualTo(1024L);
    }

    @Test
    void 팀_업로드용_key는_teams_temp_경로를_사용한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/team", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                7L,
                new PresignedUploadRequest(UploadType.TEAM, "team-image", "image/jpeg", null)
        );

        assertThat(response.objectKey()).startsWith("teams/temp/7/");
        assertThat(response.objectKey()).doesNotEndWith(".");
    }

    @Test
    void 인증사진_업로드용_key는_proofs_경로를_사용한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/proof", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                3L,
                new PresignedUploadRequest(UploadType.PROOF, "proof.webp", "image/webp", null)
        );

        assertThat(response.objectKey()).startsWith("proofs/3/");
        assertThat(response.objectKey()).endsWith(".webp");
    }

    @Test
    void 지원하지_않는_이미지_형식이면_400_예외를_던진다() {
        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.gif", "image/gif", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(s3Presigner).should(never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void 이미지_key가_비어있으면_url을_생성하지_않는다() {
        assertThat(fileService.resolveImageUrl(null)).isNull();
        assertThat(fileService.resolveImageUrl(" ")).isNull();
    }

    @Test
    void 이미지_key가_있으면_presigned_get_url을_생성한다() throws Exception {
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(
                PresignedGetObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/download", SdkHttpMethod.GET))
                        .expiration(Instant.now().plusSeconds(3600))
                        .isBrowserExecutable(true)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        String url = fileService.resolveImageUrl("profiles/1/a.png");

        assertThat(url).isEqualTo("https://storage.example.com/download");
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        then(s3Presigner).should().presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration().getSeconds()).isEqualTo(3600);
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("uploads");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("profiles/1/a.png");
    }

    @Test
    void 삭제_key가_비어있으면_s3를_호출하지_않는다() {
        fileService.deleteObject(null);
        fileService.deleteObject(" ");

        then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 삭제는_s3_예외를_삼킨다() {
        doThrow(S3Exception.builder().message("failed").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        fileService.deleteObject("profiles/1/a.png");

        then(s3Client).should().deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 썸네일은_key가_비어있으면_null을_반환한다() {
        assertThat(fileService.createProofThumbnail(null)).isNull();
        assertThat(fileService.createProofThumbnail(" ")).isNull();
    }

    @Test
    void 썸네일_생성_실패시_null을_반환한다() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2, 3}));

        assertThat(fileService.createProofThumbnail("proofs/1/a.png")).isNull();
    }

    private SdkHttpFullRequest httpRequest(String url, SdkHttpMethod method) {
        return SdkHttpFullRequest.builder()
                .uri(URI.create(url))
                .method(method)
                .build();
    }
}
