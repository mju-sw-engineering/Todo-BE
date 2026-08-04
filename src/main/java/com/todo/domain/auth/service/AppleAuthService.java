package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppleAuthService {

    private final AppleIdentityTokenService identityTokenService;
    private final AppleTokenClient appleTokenClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public sealed interface AppleLoginResult permits AppleLoginResult.LoggedIn, AppleLoginResult.SetupRequired {
        record LoggedIn(LoginResult loginResult) implements AppleLoginResult {}
        record SetupRequired(String setupToken) implements AppleLoginResult {}
    }

    @Transactional
    public AppleLoginResult appleLogin(AppleLoginRequest request) {
        String socialId = identityTokenService.verify(request.identityToken(), request.nonce());

        return userRepository.findBySocialId(socialId)
                .map(user -> {
                    String appleRefreshToken = appleTokenClient.exchangeForAppleRefreshToken(request.authorizationCode());
                    user.saveAppleRefreshToken(appleRefreshToken);
                    return (AppleLoginResult) new AppleLoginResult.LoggedIn(issueTokens(user));
                })
                .orElseGet(() -> {
                    String setupToken = jwtUtil.generateSetupToken(socialId, request.authorizationCode());
                    return new AppleLoginResult.SetupRequired(setupToken);
                });
    }

    @Transactional
    public LoginResult appleComplete(AppleCompleteRequest request) {
        Claims claims;
        try {
            claims = jwtUtil.parseSetupToken(request.setupToken());
        } catch (Exception e) {
            throw new BusinessException("유효하지 않거나 만료된 setup token입니다.", HttpStatus.BAD_REQUEST);
        }

        String socialId = claims.getSubject();
        String authorizationCode = claims.get("authCode", String.class);

        if (userRepository.findBySocialId(socialId).isPresent()) {
            throw new BusinessException("이미 가입된 Apple 계정입니다.", HttpStatus.CONFLICT);
        }

        User user = userRepository.save(User.createAppleUser(socialId, request.nickname()));

        String appleRefreshToken = appleTokenClient.exchangeForAppleRefreshToken(authorizationCode);
        user.saveAppleRefreshToken(appleRefreshToken);

        return issueTokens(user);
    }

    private LoginResult issueTokens(User user) {
        String accessToken = jwtUtil.generateToken(user.getId());
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        refreshTokenRepository.save(
                RefreshToken.create(user, rawRefreshToken, jwtUtil.refreshTokenExpiresAt())
        );
        return new LoginResult(accessToken, rawRefreshToken);
    }
}
