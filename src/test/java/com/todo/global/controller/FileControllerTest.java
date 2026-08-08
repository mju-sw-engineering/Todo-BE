package com.todo.global.controller;

import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
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

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpleRateLimiter rateLimiter;

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
        given(rateLimiter.tryAcquire(eq("presigned-upload:1.2.3.4"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 비로그인_프로필_발급은_X_Forwarded_For의_첫_IP를_기준으로_제한한다() {
        FileController controller = controller();
        PresignedUploadRequest request = profileRequest();
        MockHttpServletRequest httpRequest = httpRequest("10.0.0.1");
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        given(rateLimiter.tryAcquire(eq("presigned-upload:203.0.113.7"), anyInt(), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest))
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
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_업로드는_사용자를_찾아_userId를_전달한다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null);
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
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png", null);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_로그인이_필요하다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null);

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null, httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_인증객체가_있어도_미인증이면_401_예외를_던진다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null);

        assertThatThrownBy(() ->
                controller.generatePresignedUploadUrl(request, unauthenticatedAuth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증샷_업로드는_사용자를_찾아_userId를_전달한다() {
        FileController controller = controller();
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null);
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
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROOF, "proof.png", "image/png", null);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, auth(), httpRequest("1.2.3.4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private FileController controller() {
        return new FileController(fileService, userRepository, rateLimiter);
    }

    private PresignedUploadRequest profileRequest() {
        return new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", null);
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
