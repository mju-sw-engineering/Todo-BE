package com.todo.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "apple")
public record AppleProperties(
        @NotBlank String teamId,
        @NotBlank String webClientId,
        @NotBlank String iosClientId,
        @NotBlank String keyId,
        @NotBlank String privateKey,
        @NotBlank String publicKeyUrl,
        @NotBlank String tokenUrl,
        @NotBlank String revokeUrl
) {}
