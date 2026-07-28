package com.todo.domain.terms.controller;

import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.dto.response.TermsResponse;
import com.todo.domain.terms.service.TermsService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TermsControllerTest {

    @Mock
    private TermsService termsService;

    private AllTermsResponse sampleResponse() {
        TermsResponse terms = new TermsResponse("TERMS", "이용약관", "내용", "v1.0", "2026-07-28");
        TermsResponse privacy = new TermsResponse("PRIVACY", "개인정보 처리방침", "내용", "v1.0", "2026-07-28");
        TermsResponse marketing = new TermsResponse("MARKETING", "마케팅 수신 동의", "내용", "v1.0", "2026-07-28");
        return new AllTermsResponse(terms, privacy, marketing);
    }

    @Test
    void 전체_약관_조회_성공() {
        TermsController controller = new TermsController(termsService);
        AllTermsResponse expected = sampleResponse();
        given(termsService.getAllTerms()).willReturn(expected);

        ResponseEntity<ApiResponse<AllTermsResponse>> response = controller.getAllTerms();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(expected);
    }

    @Test
    void 내가_동의한_약관_조회_성공() {
        TermsController controller = new TermsController(termsService);
        AllTermsResponse expected = sampleResponse();
        Authentication authentication = mock(Authentication.class);
        given(authentication.getName()).willReturn("user1");
        given(termsService.getAllAgreedTerms("user1")).willReturn(expected);

        ResponseEntity<ApiResponse<AllTermsResponse>> response = controller.getAllAgreedTerms(authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(expected);
    }
}
