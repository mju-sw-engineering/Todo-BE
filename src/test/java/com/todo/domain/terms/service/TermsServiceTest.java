package com.todo.domain.terms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    private UserConsentRepository userConsentRepository;

    @InjectMocks
    private TermsService termsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(termsService, "objectMapper", new ObjectMapper());
    }

    @Test
    void 전체_약관_현재_버전_조회_성공() {
        AllTermsResponse response = termsService.getAllTerms();

        assertThat(response.terms().type()).isEqualTo("TERMS");
        assertThat(response.terms().title()).isEqualTo("이용약관");
        assertThat(response.terms().version()).isEqualTo("v1.0");
        assertThat(response.terms().content()).isNotBlank();

        assertThat(response.privacy().type()).isEqualTo("PRIVACY");
        assertThat(response.privacy().title()).isEqualTo("개인정보 처리방침");
        assertThat(response.privacy().version()).isEqualTo("v1.0");

        assertThat(response.marketing().type()).isEqualTo("MARKETING");
        assertThat(response.marketing().title()).isEqualTo("마케팅 수신 동의");
        assertThat(response.marketing().version()).isEqualTo("v1.0");
    }

    @Test
    void 내가_동의한_전체_약관_조회_성공() {
        UserConsent termsConsent = mock(UserConsent.class);
        UserConsent privacyConsent = mock(UserConsent.class);
        UserConsent marketingConsent = mock(UserConsent.class);
        given(termsConsent.getConsentVersion()).willReturn("v1.0");
        given(privacyConsent.getConsentVersion()).willReturn("v1.0");
        given(marketingConsent.getConsentVersion()).willReturn("v1.0");

        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.TERMS))
                .willReturn(Optional.of(termsConsent));
        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.PRIVACY))
                .willReturn(Optional.of(privacyConsent));
        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.MARKETING))
                .willReturn(Optional.of(marketingConsent));

        AllTermsResponse response = termsService.getAllAgreedTerms("user1");

        assertThat(response.terms().version()).isEqualTo("v1.0");
        assertThat(response.terms().type()).isEqualTo("TERMS");
        assertThat(response.privacy().type()).isEqualTo("PRIVACY");
        assertThat(response.marketing().type()).isEqualTo("MARKETING");
    }

    @Test
    void 이용약관_동의_이력_없으면_404_예외() {
        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.TERMS))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> termsService.getAllAgreedTerms("user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("동의 이력이 없습니다");
    }

    @Test
    void 마케팅_동의_이력_없으면_404_예외() {
        UserConsent termsConsent = mock(UserConsent.class);
        UserConsent privacyConsent = mock(UserConsent.class);
        given(termsConsent.getConsentVersion()).willReturn("v1.0");
        given(privacyConsent.getConsentVersion()).willReturn("v1.0");

        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.TERMS))
                .willReturn(Optional.of(termsConsent));
        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.PRIVACY))
                .willReturn(Optional.of(privacyConsent));
        given(userConsentRepository.findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc("user1", ConsentType.MARKETING))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> termsService.getAllAgreedTerms("user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("동의 이력이 없습니다");
    }

    @Test
    void 현재_버전_조회_성공() {
        String version = termsService.getCurrentVersion(ConsentType.TERMS);
        assertThat(version).isEqualTo("v1.0");
    }
}
