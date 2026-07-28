package com.todo.domain.terms.controller;

import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.service.TermsService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermsController implements TermsControllerDocs {

    private final TermsService termsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AllTermsResponse>> getAllTerms() {
        return ResponseEntity.ok(ApiResponse.success(termsService.getAllTerms()));
    }

    @GetMapping("/agreed")
    public ResponseEntity<ApiResponse<AllTermsResponse>> getAllAgreedTerms(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(termsService.getAllAgreedTerms(authentication.getName())));
    }
}
