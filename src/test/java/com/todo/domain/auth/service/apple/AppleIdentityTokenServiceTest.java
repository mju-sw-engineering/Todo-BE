package com.todo.domain.auth.service.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.global.config.AppleProperties;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.SimpleRateLimiter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AppleIdentityTokenServiceTest {

    private static final String WEB_CLIENT_ID = "com.test.app.web";
    private static final String IOS_CLIENT_ID = "com.test.app";
    private static final String SOCIAL_ID = "apple-user-001";
    private static final String PUBLIC_KEY_URL = "https://appleid.apple.com/auth/keys";

    @Mock
    private AppleProperties appleProperties;
    @Mock
    private RestClient restClient;
    @Mock
    private SimpleRateLimiter rateLimiter;

    private AppleIdentityTokenService service;
    private KeyPair rsaKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        service = new AppleIdentityTokenService(
                appleProperties, restClient, new ObjectMapper(), rateLimiter, Clock.systemUTC());
        lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    void 유효한_web_identity_token_검증_성공() throws Exception {
        given(appleProperties.webClientId()).willReturn(WEB_CLIENT_ID);
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String nonce = "test-nonce";
        String token = buildToken(WEB_CLIENT_ID, SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() + 600_000));

        AppleIdentityTokenService.VerifyResult result = service.verify(token, nonce);

        assertThat(result.socialId()).isEqualTo(SOCIAL_ID);
        assertThat(result.matchedClientId()).isEqualTo(WEB_CLIENT_ID);
    }

    @Test
    void 유효한_ios_identity_token_검증_성공() throws Exception {
        given(appleProperties.webClientId()).willReturn(WEB_CLIENT_ID);
        given(appleProperties.iosClientId()).willReturn(IOS_CLIENT_ID);
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String nonce = "test-nonce";
        String token = buildToken(IOS_CLIENT_ID, SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() + 600_000));

        AppleIdentityTokenService.VerifyResult result = service.verify(token, nonce);

        assertThat(result.socialId()).isEqualTo(SOCIAL_ID);
        assertThat(result.matchedClientId()).isEqualTo(IOS_CLIENT_ID);
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() throws Exception {
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String nonce = "test-nonce";
        String token = buildToken(IOS_CLIENT_ID, SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() - 1000));

        assertThatThrownBy(() -> service.verify(token, nonce))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonce가_불일치하면_검증에_실패한다() throws Exception {
        given(appleProperties.webClientId()).willReturn(WEB_CLIENT_ID);
        given(appleProperties.iosClientId()).willReturn(IOS_CLIENT_ID);
        mockPublicKeyFetch("test-kid", rsaKeyPair.getPublic());

        String token = buildToken(IOS_CLIENT_ID, SOCIAL_ID, sha256Hex("original-nonce"), new Date(System.currentTimeMillis() + 600_000));

        assertThatThrownBy(() -> service.verify(token, "tampered-nonce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void 토큰_형식이_올바르지_않으면_예외를_던진다() {
        assertThatThrownBy(() -> service.verify("invalid.token", "nonce"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void alg가_RS256이_아니면_공개키_조회_없이_거부한다() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"kid\":\"test-kid\"}".getBytes(StandardCharsets.UTF_8));
        String token = header + ".payload.signature";

        assertThatThrownBy(() -> service.verify(token, "nonce"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple identity token 형식이 올바르지 않습니다.");

        then(restClient).should(never()).get();
    }

    @Test
    void JWKS에_없는_kid는_기억해두고_반복_요청에_재조회하지_않는다() throws Exception {
        // JWKS에는 other-kid만 있고 토큰은 test-kid로 서명됨
        mockPublicKeyFetch("other-kid", rsaKeyPair.getPublic());
        String nonce = "test-nonce";
        String token = buildToken(IOS_CLIENT_ID, SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() + 600_000));

        assertThatThrownBy(() -> service.verify(token, nonce))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 공개키를 찾을 수 없습니다");
        assertThatThrownBy(() -> service.verify(token, nonce))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 공개키를 찾을 수 없습니다");

        // 두 번째 요청은 네거티브 캐시에서 걸러져 외부 조회가 한 번뿐이어야 한다
        then(restClient).should(times(1)).get();
    }

    @Test
    void JWKS_조회_한도를_넘으면_외부_호출_없이_거부한다() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), any())).willReturn(false);
        String nonce = "test-nonce";
        String token = buildToken(IOS_CLIENT_ID, SOCIAL_ID, sha256Hex(nonce), new Date(System.currentTimeMillis() + 600_000));

        assertThatThrownBy(() -> service.verify(token, nonce))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        then(restClient).should(never()).get();
    }

    // ── 헬퍼 ──

    private String buildToken(String audience, String sub, String nonce, Date expiration) {
        return Jwts.builder()
                .subject(sub)
                .issuer("https://appleid.apple.com")
                .audience().add(audience).and()
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
