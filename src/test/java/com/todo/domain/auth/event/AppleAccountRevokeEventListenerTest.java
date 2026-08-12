package com.todo.domain.auth.event;

import com.todo.domain.auth.service.AppleRevokeOutboxService;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AppleAccountRevokeEventListenerTest {

    @InjectMocks
    private AppleAccountRevokeEventListener listener;

    @Mock
    private AppleTokenClient appleTokenClient;
    @Mock
    private AppleRevokeOutboxService appleRevokeOutboxService;

    @Test
    void 커밋후_이벤트를_받으면_저장된_client_id로_revoke를_호출한다() {
        listener.onAppleAccountRevokeRequested(new AppleAccountRevokeRequestedEvent(1L, "apple-rt", "com.test.app"));

        then(appleTokenClient).should().revokeRefreshToken("apple-rt", "com.test.app");
        then(appleRevokeOutboxService).shouldHaveNoInteractions();
    }

    @Test
    void revoke가_실패하면_예외를_전파하지_않고_재시도용_outbox에_적재한다() {
        willThrow(new BusinessException("Apple 토큰 revoke 중 오류가 발생했습니다.", HttpStatus.BAD_REQUEST))
                .given(appleTokenClient).revokeRefreshToken("apple-rt", "com.test.app");

        assertThatCode(() -> listener.onAppleAccountRevokeRequested(
                new AppleAccountRevokeRequestedEvent(1L, "apple-rt", "com.test.app")))
                .doesNotThrowAnyException();

        then(appleRevokeOutboxService).should().enqueue(1L, "apple-rt", "com.test.app");
    }
}
