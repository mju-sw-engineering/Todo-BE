package com.todo.domain.terms.dto.request;

import com.todo.domain.auth.entity.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentRequest(
        @NotNull
        @Schema(description = "약관 유형", example = "TERMS")
        ConsentType consentType,

        @NotBlank
        @Schema(description = "동의한 약관 버전", example = "v2.0")
        String version
) {}
