package com.todo.domain.auth.service.apple;

import com.todo.global.config.AppleProperties;
import com.todo.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class AppleTokenClient {

    private static final long CLIENT_SECRET_EXPIRY = 3 * 60 * 1000L;
    private static final String APPLE_AUD = "https://appleid.apple.com";

    private final AppleProperties appleProperties;
    private final RestClient restClient;
    private PrivateKey privateKey;

    public AppleTokenClient(AppleProperties appleProperties,
                            @Qualifier("appleRestClient") RestClient restClient) {
        this.appleProperties = appleProperties;
        this.restClient = restClient;
    }

    public String exchangeForAppleRefreshToken(String authorizationCode, String clientId) {
        ensurePrivateKeyLoaded();
        String clientSecret = generateClientSecret(clientId);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", authorizationCode);
        body.add("grant_type", "authorization_code");

        try {
            Map<?, ?> response = restClient.post()
                    .uri(appleProperties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("refresh_token")) {
                throw new BusinessException("Apple 토큰 교환에 실패했습니다.", HttpStatus.BAD_REQUEST);
            }
            return (String) response.get("refresh_token");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Apple 토큰 교환 중 오류가 발생했습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    public void revokeRefreshToken(String refreshToken, String clientId) {
        ensurePrivateKeyLoaded();
        String clientSecret = generateClientSecret(clientId);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("token", refreshToken);
        body.add("token_type_hint", "refresh_token");

        try {
            restClient.post()
                    .uri(appleProperties.revokeUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BusinessException("Apple 토큰 revoke 중 오류가 발생했습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensurePrivateKeyLoaded() {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    this.privateKey = parsePrivateKey(appleProperties.privateKey());
                }
            }
        }
    }

    private String generateClientSecret(String clientId) {
        Date now = new Date();
        return Jwts.builder()
                .header().add("kid", appleProperties.keyId()).and()
                .issuer(appleProperties.teamId())
                .subject(clientId)
                .audience().add(APPLE_AUD).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + CLIENT_SECRET_EXPIRY))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Apple .p8 private key 파싱에 실패했습니다. 환경변수 APPLE_PRIVATE_KEY를 확인하세요.", e);
        }
    }
}
