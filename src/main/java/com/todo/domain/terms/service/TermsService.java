package com.todo.domain.terms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.dto.response.TermsResponse;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TermsService {

    private static final Map<ConsentType, String> TYPE_TO_FILE = Map.of(
            ConsentType.TERMS,     "terms/terms_of_service.json",
            ConsentType.PRIVACY,   "terms/privacy_policy.json",
            ConsentType.MARKETING, "terms/marketing_consent.json"
    );

    private final UserConsentRepository userConsentRepository;
    private final ObjectMapper objectMapper;

    public AllTermsResponse getAllTerms() {
        return new AllTermsResponse(
                getCurrentTerms(ConsentType.TERMS),
                getCurrentTerms(ConsentType.PRIVACY),
                getCurrentTerms(ConsentType.MARKETING)
        );
    }

    public AllTermsResponse getAllAgreedTerms(String loginId) {
        return new AllTermsResponse(
                getAgreedTerms(loginId, ConsentType.TERMS),
                getAgreedTerms(loginId, ConsentType.PRIVACY),
                getAgreedTerms(loginId, ConsentType.MARKETING)
        );
    }

    public String getCurrentVersion(ConsentType consentType) {
        Map<String, Object> json = loadJson(consentType);
        return (String) json.get("currentVersion");
    }

    private TermsResponse getCurrentTerms(ConsentType consentType) {
        Map<String, Object> json = loadJson(consentType);
        String currentVersion = (String) json.get("currentVersion");
        return buildResponse(consentType, currentVersion, json);
    }

    private TermsResponse getAgreedTerms(String loginId, ConsentType consentType) {
        UserConsent consent = userConsentRepository
                .findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(loginId, consentType)
                .orElseThrow(() -> new BusinessException("동의 이력이 없습니다.", HttpStatus.NOT_FOUND));

        Map<String, Object> json = loadJson(consentType);
        return buildResponse(consentType, consent.getConsentVersion(), json);
    }

    private Map<String, Object> loadJson(ConsentType consentType) {
        String fileName = TYPE_TO_FILE.get(consentType);
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new BusinessException("약관 파일을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private TermsResponse buildResponse(ConsentType consentType, String version, Map<String, Object> json) {
        Map<String, Object> versions = (Map<String, Object>) json.get("versions");
        Map<String, Object> versionData = (Map<String, Object>) versions.get(version);
        if (versionData == null) {
            throw new BusinessException("요청한 버전의 약관이 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }
        return new TermsResponse(
                consentType.name(),
                (String) versionData.get("title"),
                (String) versionData.get("content"),
                version,
                (String) versionData.get("updatedAt")
        );
    }
}
