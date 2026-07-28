package com.todo.domain.terms.controller;

import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Terms", description = "약관 API")
public interface TermsControllerDocs {

    @Operation(summary = "약관 전체 조회", description = "이용약관·개인정보 처리방침·마케팅 수신 동의 현재 버전을 한꺼번에 반환합니다.")
    ResponseEntity<ApiResponse<AllTermsResponse>> getAllTerms();

    @Operation(summary = "내가 동의한 약관 조회", description = "로그인한 사용자가 동의한 버전의 약관 3종을 한꺼번에 반환합니다.")
    ResponseEntity<ApiResponse<AllTermsResponse>> getAllAgreedTerms(Authentication authentication);
}
