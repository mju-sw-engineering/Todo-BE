package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService.VerifyResult;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.domain.terms.service.TermsService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppleAuthService {

    private final AppleIdentityTokenService identityTokenService;
    private final AppleTokenClient appleTokenClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserConsentRepository userConsentRepository;
    private final TermsService termsService;
    private final JwtUtil jwtUtil;
    private final TransactionTemplate transactionTemplate;

    public sealed interface AppleLoginResult permits AppleLoginResult.LoggedIn, AppleLoginResult.SetupRequired {
        record LoggedIn(LoginResult loginResult) implements AppleLoginResult {}
        record SetupRequired(String setupToken) implements AppleLoginResult {}
    }

    /**
     * Apple 서버 호출({@link AppleTokenClient#exchangeForAppleRefreshToken})이 끝날 때까지
     * DB 커넥션을 붙잡지 않도록, DB 조회/저장만 {@link #transactionTemplate}로 감싸고
     * 이 메서드 자체는 트랜잭션 밖에서 실행한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppleLoginResult appleLogin(AppleLoginRequest request) {
        VerifyResult verifyResult = identityTokenService.verify(request.identityToken(), request.nonce());
        String socialId = verifyResult.socialId();
        String clientId = verifyResult.matchedClientId();

        boolean existingUser = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> userRepository.findBySocialId(socialId).isPresent()));

        if (!existingUser) {
            String setupToken = jwtUtil.generateSetupToken(socialId, request.authorizationCode(), clientId);
            return new AppleLoginResult.SetupRequired(setupToken);
        }

        String appleRefreshToken = appleTokenClient.exchangeForAppleRefreshToken(request.authorizationCode(), clientId);

        LoginResult loginResult = transactionTemplate.execute(status -> {
            User user = userRepository.findBySocialId(socialId)
                    .orElseThrow(() -> new BusinessException("Apple 계정을 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
            user.saveAppleCredentials(appleRefreshToken, clientId);
            return issueTokens(user);
        });
        return new AppleLoginResult.LoggedIn(loginResult);
    }

    /** {@link #appleLogin}과 같은 이유로 트랜잭션 밖에서 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LoginResult appleComplete(AppleCompleteRequest request) {
        Claims claims;
        try {
            claims = jwtUtil.parseSetupToken(request.setupToken());
        } catch (Exception e) {
            throw new BusinessException("유효하지 않거나 만료된 setup token입니다.", HttpStatus.BAD_REQUEST);
        }

        String socialId = claims.getSubject();
        String authorizationCode = claims.get("authCode", String.class);
        String clientId = claims.get("clientId", String.class);

        boolean alreadyRegistered = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> userRepository.findBySocialId(socialId).isPresent()));
        if (alreadyRegistered) {
            throw new BusinessException("이미 가입된 Apple 계정입니다.", HttpStatus.CONFLICT);
        }

        String appleRefreshToken = appleTokenClient.exchangeForAppleRefreshToken(authorizationCode, clientId);

        return transactionTemplate.execute(status -> {
            User user;
            try {
                user = userRepository.save(User.createAppleUser(socialId, request.nickname(), request.profileImageKey()));
            } catch (DataIntegrityViolationException e) {
                // 위 존재 확인과 이 저장 사이에 Apple 호출로 인한 시간차가 있어, 같은 setup token으로
                // 동시에 들어온 다른 요청이 먼저 가입을 끝냈을 수 있다. social_id 유니크 제약이 막아주므로
                // 여기서 잡아 동일한 409로 응답한다.
                throw new BusinessException("이미 가입된 Apple 계정입니다.", HttpStatus.CONFLICT);
            }
            saveConsents(user, request);
            user.saveAppleCredentials(appleRefreshToken, clientId);
            return issueTokens(user);
        });
    }

    private void saveConsents(User user, AppleCompleteRequest request) {
        List<UserConsent> consents = new ArrayList<>();
        consents.add(UserConsent.create(user, ConsentType.TERMS, termsService.getCurrentVersion(ConsentType.TERMS)));
        consents.add(UserConsent.create(user, ConsentType.PRIVACY, termsService.getCurrentVersion(ConsentType.PRIVACY)));
        if (Boolean.TRUE.equals(request.marketingAgreed())) {
            consents.add(UserConsent.create(user, ConsentType.MARKETING, termsService.getCurrentVersion(ConsentType.MARKETING)));
        }
        userConsentRepository.saveAll(consents);
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
