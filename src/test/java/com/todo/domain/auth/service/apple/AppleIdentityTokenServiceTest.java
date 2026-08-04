package com.todo.domain.auth.service.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.global.config.AppleProperties;
import com.todo.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.security.*;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppleIdentityTokenServiceTest {

    private static final String CLIENT_ID = "com.test.app";
    private static final String SOCIAL_ID = "apple-user-001";
    private static final String PUBLIC_KEY_URL = "https://appleid.apple.com/auth/keys";

    @Mock
    private AppleProperties appleProperties;
    @Mock
    private RestClient restClient;

    private AppleIdentityTokenService service;
    private KeyPair rsaKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        service = new AppleIdentityTokenService(appleProperties, restClient, new ObjectMapper());
    }

    @Test
    void 유효한_identity_token_검증_성공() throws Exception {
        given(appleProperties.clientId()).willReturn(CLIENT_ID);
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String nonce = "test-nonce";
        String token = buildToken(SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() + 600_000));

        String socialId = service.verify(token, nonce);

        assertThat(socialId).isEqualTo(SOCIAL_ID);
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() throws Exception {
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String nonce = "test-nonce";
        String token = buildToken(SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() - 1000));

        assertThatThrownBy(() -> service.verify(token, nonce))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonce가_불일치하면_검증에_실패한다() throws Exception {
        given(appleProperties.clientId()).willReturn(CLIENT_ID);
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String token = buildToken(SOCIAL_ID, sha256Hex("original-nonce"), new Date(System.currentTimeMillis() + 600_000));

        assertThatThrownBy(() -> service.verify(token, "tampered-nonce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void 토큰_형식이_올바르지_않으면_예외를_던진다() {
        assertThatThrownBy(() -> service.verify("invalid.token", "nonce"))
                .isInstanceOf(BusinessException.class);
    }

    // ── 헬퍼 ──

    private String buildToken(String sub, String nonce, Date expiration) {
        return Jwts.builder()
                .subject(sub)
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .claim("nonce", nonce)
                .issuedAt(new Date())
                .expiration(expiration)
                .header().add("kid", "test-kid").and()
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @SuppressWarnings("unchecked")
    private void mockPublicKeyFetch(String kid, PublicKey publicKey) throws Exception {
        given(appleProperties.publicKeyUrl()).willReturn(PUBLIC_KEY_URL);

        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(((java.security.interfaces.RSAPublicKey) publicKey).getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(((java.security.interfaces.RSAPublicKey) publicKey).getPublicExponent().toByteArray());
        String jwks = new ObjectMapper().writeValueAsString(Map.of(
                "keys", new Object[]{Map.of("kid", kid, "kty", "RSA", "n", n, "e", e)}
        ));

        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        given(restClient.get()).willReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        given(uriSpec.uri(any(String.class))).willReturn((RestClient.RequestHeadersSpec) headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(String.class)).willReturn(jwks);
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
