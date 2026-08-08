package com.todo.domain.auth.service.apple;

import com.todo.global.config.AppleProperties;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppleTokenClientTest {

    private static final String CLIENT_ID = "com.test.app";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

    @Mock private AppleProperties appleProperties;
    @Mock private RestClient restClient;

    private AppleTokenClient appleTokenClient;
    private String ecPrivateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = kpg.generateKeyPair();
        ecPrivateKeyPem = "-----BEGIN PRIVATE KEY-----"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "-----END PRIVATE KEY-----";

        appleTokenClient = new AppleTokenClient(appleProperties, restClient);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void authorizationCode로_Apple_refresh_token을_교환한다() {
        given(appleProperties.privateKey()).willReturn(ecPrivateKeyPem);
        given(appleProperties.teamId()).willReturn("TEAM123456");
        given(appleProperties.keyId()).willReturn("KEY1234567");
        given(appleProperties.tokenUrl()).willReturn(TOKEN_URL);

        // RETURNS_SELF로 fluent builder 체인 처리
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Mockito.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        given(restClient.post()).willReturn(uriSpec);
        given(uriSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(Map.class)).willReturn(Map.of("refresh_token", "apple-rt-xyz"));

        String result = appleTokenClient.exchangeForAppleRefreshToken("auth-code-123", CLIENT_ID);

        assertThat(result).isEqualTo("apple-rt-xyz");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void Apple_서버가_refresh_token을_반환하지_않으면_예외를_던진다() {
        given(appleProperties.privateKey()).willReturn(ecPrivateKeyPem);
        given(appleProperties.teamId()).willReturn("TEAM123456");
        given(appleProperties.keyId()).willReturn("KEY1234567");
        given(appleProperties.tokenUrl()).willReturn(TOKEN_URL);

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Mockito.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        given(restClient.post()).willReturn(uriSpec);
        given(uriSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(Map.class)).willReturn(Map.of("error", "invalid_grant"));

        assertThatThrownBy(() -> appleTokenClient.exchangeForAppleRefreshToken("bad-code", CLIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 토큰 교환에 실패했습니다.");
    }
}
