package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService.VerifyResult;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppleAuthServiceTest {

    @InjectMocks
    private AppleAuthService appleAuthService;

    @Mock private AppleIdentityTokenService identityTokenService;
    @Mock private AppleTokenClient appleTokenClient;
    @Mock private UserRepository userRepository;
    @Mock private SessionService sessionService;
    @Mock private UserConsentRecorder userConsentRecorder;
    @Mock private JwtUtil jwtUtil;
    @Mock private com.todo.global.service.FileService fileService;
    @Mock private TransactionTemplate transactionTemplate;

    @Captor private ArgumentCaptor<User> userCaptor;

    /**
     * appleLogin/appleComplete가 TransactionTemplate으로 직접 트랜잭션을 감싸므로,
     * execute()가 실제로 콜백을 실행하도록 매 테스트마다 이어준다.
     */
    @BeforeEach
    void stubTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private static final String SOCIAL_ID = "apple-user-001";
    private static final String AUTH_CODE = "auth-code-xyz";
    private static final String NONCE = "random-nonce";
    private static final String CLIENT_ID = "com.test.app";
    private static final String EMAIL = "someone@privaterelay.appleid.com";

    @Test
    void 기존_Apple_유저_로그인_성공() {
        User user = User.createAppleUser(SOCIAL_ID, "기존닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(identityTokenService.verify(any(), any())).willReturn(new VerifyResult(SOCIAL_ID, CLIENT_ID, null));
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.of(user));
        given(appleTokenClient.exchangeForAppleRefreshToken(AUTH_CODE, CLIENT_ID)).willReturn("apple-rt");
        given(jwtUtil.generateToken(1L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("refresh-uuid");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(expiresAt);

        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(loginRequest());

        assertThat(result).isInstanceOf(AppleAuthService.AppleLoginResult.LoggedIn.class);
        AppleAuthService.AppleLoginResult.LoggedIn logged = (AppleAuthService.AppleLoginResult.LoggedIn) result;
        assertThat(logged.loginResult().accessToken()).isEqualTo("access-token");
        assertThat(user.getAppleRefreshToken()).isEqualTo("apple-rt");
        assertThat(user.getAppleClientId()).isEqualTo(CLIENT_ID);
        then(sessionService).should().issueRefreshToken(user, "refresh-uuid", "device-1", expiresAt);
    }

    @Test
    void 신규_Apple_유저_로그인시_setup_token을_반환한다() {
        given(identityTokenService.verify(any(), any())).willReturn(new VerifyResult(SOCIAL_ID, CLIENT_ID, EMAIL));
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.empty());
        given(jwtUtil.generateSetupToken(SOCIAL_ID, AUTH_CODE, CLIENT_ID, EMAIL)).willReturn("setup-token-abc");

        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(loginRequest());

        assertThat(result).isInstanceOf(AppleAuthService.AppleLoginResult.SetupRequired.class);
        AppleAuthService.AppleLoginResult.SetupRequired setup = (AppleAuthService.AppleLoginResult.SetupRequired) result;
        assertThat(setup.setupToken()).isEqualTo("setup-token-abc");
    }

    @Test
    void Apple_가입_완료시_프로필과_이메일과_동의가_함께_저장된다() {
        givenSetupToken(EMAIL);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);

        User saved = stubSavedUser();
        LocalDateTime expiresAt = givenTokenIssuance();

        LoginResult result = appleAuthService.appleComplete(completeRequest("profile-key.png", true));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(saved.getAppleRefreshToken()).isEqualTo("apple-rt");
        assertThat(saved.getAppleClientId()).isEqualTo(CLIENT_ID);

        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userCaptor.getValue().getProfileImageUrl()).isEqualTo("profile-key.png");

        // 이메일 가입과 같은 동의 이력을 남겨야 한다.
        then(userConsentRecorder).should().recordSignupConsents(saved, true);
        then(sessionService).should().issueRefreshToken(saved, "refresh-uuid", "device-1", expiresAt);
    }

    @Test
    void 마케팅_미동의시_동의_기록에도_그대로_전달된다() {
        givenSetupToken(null);
        stubSavedUser();
        givenTokenIssuance();

        appleAuthService.appleComplete(completeRequest(null, false));

        then(userConsentRecorder).should().recordSignupConsents(any(User.class), eq(false));
    }

    @Test
    void 이미_쓰이는_이메일이면_이메일_없이_가입시킨다() {
        givenSetupToken(EMAIL);
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        stubSavedUser();
        givenTokenIssuance();

        appleAuthService.appleComplete(completeRequest(null, false));

        // 409로 막으면 그 사람은 Apple 로그인을 영영 못 쓰게 되므로 이메일만 비운다.
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void 가입_완료는_본인이_업로드하지_않은_프로필_키면_거부한다() {
        givenSetupToken(EMAIL);
        org.mockito.BDDMockito.willThrow(
                        new BusinessException("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.", org.springframework.http.HttpStatus.BAD_REQUEST))
                .given(fileService).validateProfileImageKey(null, "proofs/2/stolen.jpg");

        assertThatThrownBy(() -> appleAuthService.appleComplete(completeRequest("proofs/2/stolen.jpg", true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인이 업로드한 프로필 이미지");

        then(userRepository).should(org.mockito.Mockito.never()).save(any(User.class));
        then(appleTokenClient).should(org.mockito.Mockito.never()).exchangeForAppleRefreshToken(any(), any());
    }

    @Test
    void 유효하지_않은_setup_token으로_complete시_예외를_던진다() {
        given(jwtUtil.parseSetupToken("invalid-token")).willThrow(new RuntimeException("expired"));

        assertThatThrownBy(() -> appleAuthService.appleComplete(
                new AppleCompleteRequest("invalid-token", "닉네임", null, true, true, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("setup token");
    }

    @Test
    void 이미_가입된_소셜_ID로_complete시_예외를_던진다() {
        givenSetupToken(null);
        given(userRepository.findBySocialId(SOCIAL_ID))
                .willReturn(Optional.of(User.createAppleUser(SOCIAL_ID, "기존", null)));

        assertThatThrownBy(() -> appleAuthService.appleComplete(completeRequest(null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 가입된");
    }

    @Test
    void 존재확인_이후_저장_시점에_경합으로_유니크_제약을_위반하면_409를_던진다() {
        givenSetupToken(null);
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.empty());
        given(appleTokenClient.exchangeForAppleRefreshToken(AUTH_CODE, CLIENT_ID)).willReturn("apple-rt");
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("uk_users_social_id"));

        assertThatThrownBy(() -> appleAuthService.appleComplete(completeRequest(null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 가입된");
    }

    private void givenSetupToken(String email) {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(SOCIAL_ID);
        given(claims.get("authCode", String.class)).willReturn(AUTH_CODE);
        given(claims.get("clientId", String.class)).willReturn(CLIENT_ID);
        given(claims.get("email", String.class)).willReturn(email);
        given(jwtUtil.parseSetupToken("setup-token")).willReturn(claims);
    }

    private User stubSavedUser() {
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.empty());
        User saved = User.createAppleUser(SOCIAL_ID, "새닉네임", null);
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        return saved;
    }

    private LocalDateTime givenTokenIssuance() {
        given(appleTokenClient.exchangeForAppleRefreshToken(AUTH_CODE, CLIENT_ID)).willReturn("apple-rt");
        given(jwtUtil.generateToken(2L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("refresh-uuid");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(expiresAt);
        return expiresAt;
    }

    private AppleCompleteRequest completeRequest(String profileImageKey, boolean marketingAgreed) {
        return new AppleCompleteRequest("setup-token", "새닉네임", profileImageKey, true, true, marketingAgreed, "device-1");
    }

    private AppleLoginRequest loginRequest() {
        return new AppleLoginRequest("identity-token", AUTH_CODE, NONCE, "device-1");
    }
}
