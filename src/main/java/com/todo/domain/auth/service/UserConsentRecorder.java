package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 가입 시점의 약관 동의를 기록한다.
 *
 * <p>이메일 가입과 Apple 가입이 각자 동의를 저장하면 약관 버전 상수가 두 곳으로 갈라져
 * 다음 개정 때 한쪽만 올라가는 사고가 난다. 어느 경로로 가입하든 같은 레코드가 남도록
 * 한 곳에 모아둔다.
 */
@Component
@RequiredArgsConstructor
public class UserConsentRecorder {

    static final String TERMS_VERSION = "v1.0";

    private final UserConsentRepository userConsentRepository;

    /**
     * 필수 동의(이용약관·개인정보)를 기록하고, 마케팅은 동의한 경우에만 남긴다.
     *
     * <p>필수 항목의 동의 여부 검증은 요청 DTO의 제약(@AssertTrue)이 담당한다.
     */
    public void recordSignupConsents(User user, boolean marketingAgreed) {
        List<UserConsent> consents = new ArrayList<>();
        consents.add(UserConsent.create(user, ConsentType.TERMS, TERMS_VERSION));
        consents.add(UserConsent.create(user, ConsentType.PRIVACY, TERMS_VERSION));
        if (marketingAgreed) {
            consents.add(UserConsent.create(user, ConsentType.MARKETING, TERMS_VERSION));
        }
        userConsentRepository.saveAll(consents);
    }
}
