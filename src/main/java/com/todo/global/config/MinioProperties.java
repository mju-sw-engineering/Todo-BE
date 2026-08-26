package com.todo.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private long presignedUrlExpiration = 600;
    private long putPresignedUrlExpiration = 600;
    private Duration apiCallTimeout = Duration.ofSeconds(30);
    private Duration apiCallAttemptTimeout = Duration.ofSeconds(10);
}
