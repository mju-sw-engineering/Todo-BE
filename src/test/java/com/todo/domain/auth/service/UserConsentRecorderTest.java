package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserConsentRecorderTest {

    @InjectMocks
    private UserConsentRecorder userConsentRecorder;

    @Mock
    private UserConsentRepository userConsentRepository;

    @Captor
    private ArgumentCaptor<List<UserConsent>> consentCaptor;

    @Test
    void 마케팅_미동의시_필수_동의_2건만_저장한다() {
        User user = User.create("user1", "encoded", "닉네임", null);

        userConsentRecorder.recordSignupConsents(user, false);

        then(userConsentRepository).should().saveAll(consentCaptor.capture());
        assertThat(consentCaptor.getValue()).extracting(UserConsent::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY);
    }

    @Test
    void 마케팅_동의시_3건을_저장한다() {
        User user = User.create("user1", "encoded", "닉네임", null);

        userConsentRecorder.recordSignupConsents(user, true);

        then(userConsentRepository).should().saveAll(consentCaptor.capture());
        assertThat(consentCaptor.getValue()).extracting(UserConsent::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.MARKETING);
    }

    @Test
    void 동의_버전을_함께_기록한다() {
        // 버전 없이 기록하면 약관 개정 후 누가 어느 버전에 동의했는지 되짚을 수 없다.
        User user = User.create("user1", "encoded", "닉네임", null);

        userConsentRecorder.recordSignupConsents(user, false);

        then(userConsentRepository).should().saveAll(consentCaptor.capture());
        assertThat(consentCaptor.getValue()).allSatisfy(consent ->
                assertThat(consent.getConsentVersion()).isEqualTo(UserConsentRecorder.TERMS_VERSION));
    }
}
