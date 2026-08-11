package com.todo.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final long SETUP_TOKEN_EXPIRATION = 5 * 60 * 1000L;

    private final SecretKey secretKey;
    private final long expiration;
    private final long refreshExpiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration,
                   @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /**
     * 애플 가입 2단계까지 1단계의 정보를 넘긴다.
     *
     * <p>{@code email}은 Apple이 최초 인증에만 실어주므로 여기서 붙들어두지 않으면
     * 2단계에서 되찾을 방법이 없다. 재로그인으로 들어온 경우에는 null일 수 있다.
     */
    public String generateSetupToken(String socialId, String authorizationCode, String clientId, String email) {
        return Jwts.builder()
                .subject(socialId)
                .claim("authCode", authorizationCode)
                .claim("clientId", clientId)
                .claim("email", email)
                .claim("type", "SETUP")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SETUP_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseSetupToken(String token) {
        Claims claims = getClaims(token);
        if (!"SETUP".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("유효하지 않은 setup token입니다.");
        }
        return claims;
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
    }

    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
