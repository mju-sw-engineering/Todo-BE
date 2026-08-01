package com.todo.global.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    private final S3Config s3Config = new S3Config();

    @Test
    void S3_클라이언트에_전체_호출과_개별_시도_제한시간을_설정한다() {
        MinioProperties properties = minioProperties();
        properties.setApiCallTimeout(Duration.ofSeconds(40));
        properties.setApiCallAttemptTimeout(Duration.ofSeconds(15));

        try (S3Client s3Client = s3Config.s3Client(properties)) {
            assertThat(s3Client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout())
                    .contains(Duration.ofSeconds(40));
            assertThat(s3Client.serviceClientConfiguration().overrideConfiguration().apiCallAttemptTimeout())
                    .contains(Duration.ofSeconds(15));
        }
    }

    private MinioProperties minioProperties() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setRegion("us-east-1");
        properties.setBucket("test-bucket");
        return properties;
    }
}
