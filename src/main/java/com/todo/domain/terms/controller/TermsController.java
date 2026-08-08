package com.todo.domain.terms.controller;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.terms.dto.request.ConsentRequest;
import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.dto.response.VersionCheckItem;
import com.todo.domain.terms.service.TermsService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermsController implements TermsControllerDocs {

    private final TermsService termsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AllTermsResponse>> getAllTerms() {
        return ResponseEntity.ok(ApiResponse.success(termsService.getAllTerms(), "약관 목록을 조회했습니다"));
    }

    @GetMapping("/agreed")
    public ResponseEntity<ApiResponse<AllTermsResponse>> getAllAgreedTerms(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(termsService.getAllAgreedTerms(authentication.getName()), "동의한 약관 목록을 조회했습니다"));
    }

    @PostMapping("/consents")
    public ResponseEntity<ApiResponse<Void>> saveConsent(
            @Valid @RequestBody ConsentRequest request,
            Authentication authentication
    ) {
        termsService.saveConsent(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "약관 동의가 저장되었습니다"));
    }

    @GetMapping("/version-check")
    public ResponseEntity<ApiResponse<Map<ConsentType, VersionCheckItem>>> getVersionCheck(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(termsService.getVersionCheck(authentication.getName()), "약관 버전을 확인했습니다"));
    }
}
