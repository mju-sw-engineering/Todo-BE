package com.todo.global.controller;

import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.dto.UploadType;
import com.todo.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.ClientIpResolver;
import com.todo.global.ratelimit.SimpleRateLimiter;
import com.todo.global.response.ApiResponse;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpleRateLimiter rateLimiter;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private JwtUtil jwtUtil;

    @Test
    void 프로필_업로드는_비로그인도_허용한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/temp/a.png");
        givenRateLimitAllowed();
        given(fileService.generatePresignedPutUrl(null, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 비로그인_프로필_발급이_한도를_넘으면_429_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:1.2.3.4"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 비로그인_프로필_발급은_프록시가_붙인_마지막_IP를_기준으로_제한한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        MockHttpServletRequest httpRequest = httpRequest("10.0.0.1");
        // 앞쪽 값은 클라이언트가 위조해 보낼 수 있고, 마지막 값만 프록시가 관찰한 주소다.
        httpRequest.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7");
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:203.0.113.7"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 위조된_X_Forwarded_For_앞_IP로는_한도를_우회할_수_없다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        MockHttpServletRequest first = httpRequest("10.0.0.1");
        first.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7");
        MockHttpServletRequest second = httpRequest("10.0.0.1");
        second.addHeader("X-Forwarded-For", "2.2.2.2, 203.0.113.7");
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:203.0.113.7"), anyInt(), any(Duration.class)))
                .willReturn(false);

        // 앞 IP를 바꿔가며 보내도 같은 버킷에 걸린다.
        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, first))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, second))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 프록시가_두_단이면_오른쪽에서_두_번째_IP를_신뢰한다() {
        FileController controller = controller(2);
        PresignedUploadRequest request = profileRequest();
        MockHttpServletRequest httpRequest = httpRequest("10.0.0.1");
        // 위조값, 실제 클라이언트, CDN 엣지 순. CDN을 한 단 더 두면 신뢰 지점이 왼쪽으로 밀린다.
        httpRequest.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7, 198.51.100.2");
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:203.0.113.7"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 헤더가_신뢰_홉_수보다_짧으면_가장_왼쪽_IP를_쓴다() {
        FileController controller = controller(3);
        PresignedUploadRequest request = profileRequest();
        MockHttpServletRequest httpRequest = httpRequest("10.0.0.1");
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.7");
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:203.0.113.7"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 이메일_인증_토큰이_오면_IP가_아닌_가입자_단위로_제한한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest("verify-token");
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/temp/a.png");
        given(emailVerificationService.findVerifiedEmail("verify-token"))
                .willReturn(Optional.of("user@mju.ac.kr"));
        given(rateLimiter.tryAcquire(eq("presigned-upload:signup:user@mju.ac.kr"), anyInt(), any(Duration.class)))
                .willReturn(true);
        given(fileService.generatePresignedPutUrl(null, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 애플_setup_토큰이_오면_토큰의_이메일로_제한한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest("setup-token");
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/temp/a.png");
        Claims claims = mock(Claims.class);
        given(claims.get("email", String.class)).willReturn("apple@privaterelay.appleid.com");
        given(emailVerificationService.findVerifiedEmail("setup-token")).willReturn(Optional.empty());
        given(jwtUtil.parseSetupToken("setup-token")).willReturn(claims);
        given(rateLimiter.tryAcquire(
                eq("presigned-upload:signup:apple@privaterelay.appleid.com"), anyInt(), any(Duration.class)))
                .willReturn(true);
        given(fileService.generatePresignedPutUrl(null, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 알_수_없는_토큰은_신원으로_인정하지_않고_IP로_제한한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest("forged-token");
        given(emailVerificationService.findVerifiedEmail("forged-token")).willReturn(Optional.empty());
        given(jwtUtil.parseSetupToken("forged-token"))
                .willThrow(new IllegalArgumentException("유효하지 않은 setup token입니다."));
        given(rateLimiter.tryAcquire(eq("presigned-upload:ip:1.2.3.4"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 가입자_단위_한도를_넘으면_429_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest("verify-token");
        given(emailVerificationService.findVerifiedEmail("verify-token"))
                .willReturn(Optional.of("user@mju.ac.kr"));
        given(rateLimiter.tryAcquire(eq("presigned-upload:signup:user@mju.ac.kr"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 로그인_프로필_업로드는_rate_limit을_적용하지_않는다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/1/a.png");
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(fileService.generatePresignedPutUrl(1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_업로드는_로그인이_필요하다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null, null);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_업로드는_사용자를_찾아_userId를_전달한다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null, null);
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "teams/temp/1/a.png");
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(fileService.generatePresignedPutUrl(1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_업로드는_사용자가_없으면_401_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null, null);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_로그인이_필요하다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null, null);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_인증객체가_있어도_미인증이면_401_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null, null);

        assertThatThrownBy(() ->
                controller.generatePresignedUploadUrl(request, unauthenticatedAuth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_사용자를_찾아_userId를_전달한다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null, null);
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "proofs/1/a.png");
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(fileService.generatePresignedPutUrl(1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4"));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 인증샷_업로드는_사용자가_없으면_401_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null, null);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private FileController controller() {
        return controller(1);
    }

    private FileController controller(int trustedProxyHops) {
        return new FileController(
                fileService, userRepository, rateLimiter, emailVerificationService, jwtUtil,
                new ClientIpResolver(trustedProxyHops));
    }

    private PresignedUploadRequest profileRequest() {
        return profileRequest(null);
    }

    private PresignedUploadRequest profileRequest(String signupToken) {
        return new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", null, signupToken);
    }

    private void givenRateLimitAllowed() {
        given(rateLimiter.tryAcquire(any(String.class), anyInt(), any(Duration.class))).willReturn(true);
    }

    private MockHttpServletRequest httpRequest(String remoteAddr) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr(remoteAddr);
        return httpRequest;
    }

    private TestingAuthenticationToken auth() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("1", null);
        authentication.setAuthenticated(true);
        return authentication;
    }

    private TestingAuthenticationToken unauthenticatedAuth() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("1", null);
        authentication.setAuthenticated(false);
        return authentication;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
