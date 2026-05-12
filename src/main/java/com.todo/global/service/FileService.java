package com.todo.global.service;

import com.todo.global.config.MinioProperties;
import com.todo.global.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Client s3Client;
    private final MinioProperties props;

    @PostConstruct
    public void initBuckets() {
        ensureBucketExists(props.getBucket().getProfiles());
        ensureBucketExists(props.getBucket().getTeams());
    }

    public String saveProfileImage(MultipartFile file) {
        return upload(file, props.getBucket().getProfiles());
    }

    public String saveTeamImage(MultipartFile file) {
        return upload(file, props.getBucket().getTeams());
    }

    private String upload(MultipartFile file, String bucket) {
        String ext = extractExtension(file.getOriginalFilename());
        String key = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new BusinessException("파일을 읽는 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (S3Exception e) {
            throw new BusinessException("스토리지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return key;
    }

    private void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
