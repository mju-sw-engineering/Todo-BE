package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.FindIdRequest;
import com.todo.domain.auth.dto.request.FindPasswordRequest;
import com.todo.domain.auth.dto.request.ResetPasswordRequest;
import com.todo.domain.auth.dto.response.FindIdResponse;
import com.todo.domain.auth.dto.response.FindPasswordResponse;
import com.todo.domain.auth.entity.AuthProvider;
import com.todo.domain.auth.entity.PasswordResetToken;
import com.todo.domain.auth.repository.PasswordResetTokenRepository;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 이메일(LOCAL) 계정 전용 아이디/비밀번호 찾기. 기존 이메일 인증 인프라
 * ({@link EmailVerificationService})로 이메일 소유를 증명받은 뒤에만 동작한다.
 *
 * <p>{@link AuthService}에 얹지 않는다. {@code AuthService}는 로그인/회원가입/토큰 발급으로
 * 범위가 좁게 정해져 있고, 계정 복구는 성격이 다르다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRecoveryService {

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    /**
     * emailVerificationService.validateAndConsume가 이메일 인증 토큰을 소비(쓰기)하므로
     * readOnly로 두면 안 된다.
     */
    @Transactional
    public FindIdResponse findLoginId(FindIdRequest request) {
        emailVerificationService.validateAndConsume(request.emailVerificationToken(), request.email());

        User user = findLocalUserByEmail(request.email());

        return FindIdResponse.from(user);
    }

    @Transactional
    public FindPasswordResponse initiatePasswordReset(FindPasswordRequest request) {
        emailVerificationService.validateAndConsume(request.emailVerificationToken(), request.email());

        User user = findLocalUserByEmail(request.email());

        // 재발급하면 이전 미사용 토큰은 무효가 된다.
        passwordResetTokenRepository.deleteByUserId(user.getId());

        String rawToken = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(RESET_TOKEN_TTL);
        passwordResetTokenRepository.save(PasswordResetToken.create(user, hash(rawToken), expiresAt));

        return FindPasswordResponse.of(rawToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash(request.passwordResetToken()))
                .orElseThrow(() -> new BusinessException("유효하지 않은 재설정 토큰입니다.", HttpStatus.UNAUTHORIZED));

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.isUsed() || token.isExpired(now)) {
            throw new BusinessException("만료되었거나 이미 사용된 재설정 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        // markAsUsed가 영속성 컨텍스트를 비우므로(clearAutomatically) 그 전에 읽어 둔다.
        User user = token.getUser();

        // 실제 1회용 보장은 여기서 이뤄진다. 위 검증과 이 UPDATE 사이에 다른 요청이 먼저
        // 소비했다면 갱신 행이 0이 되고, 그 요청만 통과한다.
        if (passwordResetTokenRepository.markAsUsed(token.getId(), now) != 1) {
            throw new BusinessException("만료되었거나 이미 사용된 재설정 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        // user는 위 markAsUsed로 detach된 상태라, 변경 후 명시적으로 저장해야 반영된다.
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // 재설정은 계정 탈취 복구 수단이다. 기존 세션을 남기면 토큰을 쥔 공격자가
        // 재설정 후에도 refresh로 접근을 이어갈 수 있으므로 전 기기를 로그아웃시킨다.
        refreshTokenRepository.deleteByUserId(user.getId());
        notificationService.send(user, null, notificationMessageFactory.passwordChanged(), null, null);
    }

    private User findLocalUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("해당 이메일로 가입된 계정이 없습니다.", HttpStatus.NOT_FOUND));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new BusinessException("이메일 계정 전용 기능입니다. Apple 계정은 이 방법을 이용할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        return user;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 조회 키이자 저장 형태. 토큰이 이미 고엔트로피 난수라 느린 해시가 필요 없고,
     * 조회에 쓰이므로 salt 없이 결정적이어야 한다. (ReauthService와 동일한 이유)
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
