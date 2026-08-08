package com.todo.domain.terms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.terms.dto.request.ConsentRequest;
import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.dto.response.TermsResponse;
import com.todo.domain.terms.dto.response.VersionCheckItem;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AllTermsResponse getAllTerms() {
        return new AllTermsResponse(
                getCurrentTerms(ConsentType.TERMS),
                getCurrentTerms(ConsentType.PRIVACY),
                getCurrentTerms(ConsentType.MARKETING)
        );
    }

    public AllTermsResponse getAllAgreedTerms(String userId) {
        return new AllTermsResponse(
                getAgreedTerms(userId, ConsentType.TERMS),
                getAgreedTerms(userId, ConsentType.PRIVACY),
                getAgreedTerms(userId, ConsentType.MARKETING)
        );
    }

    @Transactional
    public void saveConsent(String userId, ConsentRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        boolean alreadyAgreed = userConsentRepository
                .findTopByUserIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(Long.parseLong(userId), request.consentType())
                .map(c -> c.getConsentVersion().equals(request.version()))
                .orElse(false);

        if (alreadyAgreed) {
            throw new BusinessException("이미 해당 버전에 동의하셨습니다.", HttpStatus.CONFLICT);
        }

        userConsentRepository.save(UserConsent.create(user, request.consentType(), request.version()));
    }

    public Map<ConsentType, VersionCheckItem> getVersionCheck(String userId) {
        Map<ConsentType, VersionCheckItem> result = new LinkedHashMap<>();
        for (ConsentType type : ConsentType.values()) {
            String latestVersion = getCurrentVersion(type);
            Optional<UserConsent> consent = userConsentRepository
                    .findTopByUserIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(Long.parseLong(userId), type);
            String agreedVersion = consent.map(UserConsent::getConsentVersion).orElse(null);
            boolean needsConsent = agreedVersion != null && !agreedVersion.equals(latestVersion);
            result.put(type, new VersionCheckItem(agreedVersion, latestVersion, needsConsent));
        }
        return result;
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

    private TermsResponse getAgreedTerms(String userId, ConsentType consentType) {
        UserConsent consent = userConsentRepository
                .findTopByUserIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(Long.parseLong(userId), consentType)
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
