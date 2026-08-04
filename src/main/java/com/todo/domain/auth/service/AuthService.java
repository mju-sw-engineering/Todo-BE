package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private static final String TERMS_VERSION = "v1.0";

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileService fileService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException("이미 사용 중인 아이디입니다.", HttpStatus.CONFLICT);
        }

        emailVerificationService.validateAndConsume(request.emailVerificationToken(), request.email());

        User created = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.profileImageKey()
        );
        created.assignEmail(request.email());
        User user = userRepository.save(created);

        List<UserConsent> consents = new ArrayList<>();
        consents.add(UserConsent.create(user, ConsentType.TERMS, TERMS_VERSION));
        consents.add(UserConsent.create(user, ConsentType.PRIVACY, TERMS_VERSION));
        if (Boolean.TRUE.equals(request.marketingAgreed())) {
            consents.add(UserConsent.create(user, ConsentType.MARKETING, TERMS_VERSION));
        }
        userConsentRepository.saveAll(consents);

        return SignupResponse.from(user).withImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()));
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        String accessToken = jwtUtil.generateToken(user.getId());
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        refreshTokenRepository.save(
                RefreshToken.create(user, rawRefreshToken, jwtUtil.refreshTokenExpiresAt())
        );
        return new LoginResult(accessToken, rawRefreshToken);
    }

    @Transactional
    public LoginResult refresh(String rawToken) {
        if (rawToken == null) {
            throw new BusinessException("리프레시 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        RefreshToken token = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessException("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED));

        if (token.isUsed()) {
            // 재사용 감지 — 해당 사용자의 모든 토큰 삭제
            refreshTokenRepository.deleteByUserId(token.getUser().getId());
            throw new BusinessException("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new BusinessException("만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        token.markAsUsed();

        User user = token.getUser();
        String newRawToken = jwtUtil.generateRefreshToken();
        refreshTokenRepository.save(
                RefreshToken.create(user, newRawToken, jwtUtil.refreshTokenExpiresAt())
        );

        return new LoginResult(jwtUtil.generateToken(user.getId()), newRawToken);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        refreshTokenRepository.findByToken(rawToken).ifPresent(token -> {
            if (!token.isUsed()) {
                refreshTokenRepository.delete(token);
            }
        });
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return org.springframework.security.core.userdetails.User.builder()
                .username(userId)
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles("USER")
                .build();
    }
}
