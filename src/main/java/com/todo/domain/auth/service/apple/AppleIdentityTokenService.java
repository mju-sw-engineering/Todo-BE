package com.todo.domain.auth.service.apple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.global.config.AppleProperties;
import com.todo.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppleIdentityTokenService {

    private static final String APPLE_ISS = "https://appleid.apple.com";

    private final AppleProperties appleProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    public AppleIdentityTokenService(AppleProperties appleProperties,
                                     @Qualifier("appleRestClient") RestClient restClient,
                                     ObjectMapper objectMapper) {
        this.appleProperties = appleProperties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String verify(String identityToken, String nonce) {
        String kid = extractKid(identityToken);
        PublicKey publicKey = publicKeyCache.computeIfAbsent(kid, this::fetchPublicKey);

        try {
            return parseAndValidate(identityToken, nonce, publicKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 서명 불일치 시 캐시 갱신 후 1회 재시도
            publicKeyCache.remove(kid);
            PublicKey freshKey = fetchPublicKey(kid);
            publicKeyCache.put(kid, freshKey);
            try {
                return parseAndValidate(identityToken, nonce, freshKey);
            } catch (Exception ex) {
                throw new BusinessException("Apple identity token 검증에 실패했습니다.", HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private String parseAndValidate(String identityToken, String nonce, PublicKey publicKey) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(identityToken)
                .getPayload();

        if (!APPLE_ISS.equals(claims.getIssuer())) {
            throw new BusinessException("Apple identity token의 issuer가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }
        if (!appleProperties.clientId().equals(claims.getAudience().iterator().next())) {
            throw new BusinessException("Apple identity token의 audience가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }
        validateNonce(nonce, claims.get("nonce", String.class));

        return claims.getSubject();
    }

    private void validateNonce(String rawNonce, String tokenNonce) {
        if (rawNonce == null || tokenNonce == null) {
            throw new BusinessException("nonce가 없습니다.", HttpStatus.UNAUTHORIZED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawNonce.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            if (!hex.toString().equals(tokenNonce)) {
                throw new BusinessException("nonce가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("nonce 검증 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extractKid(String identityToken) {
        try {
            String header = identityToken.split("\\.")[0];
            byte[] decoded = Base64.getUrlDecoder().decode(header);
            JsonNode node = objectMapper.readTree(decoded);
            return node.get("kid").asText();
        } catch (Exception e) {
            throw new BusinessException("Apple identity token 형식이 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    private PublicKey fetchPublicKey(String kid) {
        try {
            String response = restClient.get()
                    .uri(appleProperties.publicKeyUrl())
                    .retrieve()
                    .body(String.class);

            JsonNode keys = objectMapper.readTree(response).get("keys");
            for (JsonNode key : keys) {
                if (kid.equals(key.get("kid").asText())) {
                    return toPublicKey(key);
                }
            }
            throw new BusinessException("Apple 공개키를 찾을 수 없습니다. kid=" + kid, HttpStatus.UNAUTHORIZED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Apple 공개키 조회에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private PublicKey toPublicKey(JsonNode jwk) {
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            BigInteger modulus = new BigInteger(1, decoder.decode(jwk.get("n").asText()));
            BigInteger exponent = new BigInteger(1, decoder.decode(jwk.get("e").asText()));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new BusinessException("Apple 공개키 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
