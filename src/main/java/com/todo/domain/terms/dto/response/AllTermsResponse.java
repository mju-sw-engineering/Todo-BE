package com.todo.domain.terms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AllTermsResponse(
        @Schema(description = "이용약관")
        TermsResponse terms,

        @Schema(description = "개인정보 처리방침")
        TermsResponse privacy,

        @Schema(description = "마케팅 수신 동의")
        TermsResponse marketing
) {}
