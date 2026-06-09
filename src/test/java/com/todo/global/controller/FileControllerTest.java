package com.todo.global.controller;

import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;
    @Mock
    private UserRepository userRepository;

    @Test
    void 프로필_업로드는_비로그인도_허용한다() {
        FileController controller = new FileController(fileService, userRepository);
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png");
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/temp/a.png");
        given(fileService.generatePresignedPutUrl(null, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, null);

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 프로필_업로드는_로그인_사용자_id를_전달한다() {
        FileController controller = new FileController(fileService, userRepository);
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png");
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "profiles/1/a.png");
        User user = userWithId(1L);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(fileService.generatePresignedPutUrl(1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_업로드는_로그인이_필요하다() {
        FileController controller = new FileController(fileService, userRepository);
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png");

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 업로드는 로그인이 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_업로드는_사용자를_찾아_userId를_전달한다() {
        FileController controller = new FileController(fileService, userRepository);
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png");
        PresignedUploadResponse serviceResponse = new PresignedUploadResponse("https://upload", "teams/temp/1/a.png");
        User user = userWithId(1L);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(fileService.generatePresignedPutUrl(1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<PresignedUploadResponse>> response =
                controller.generatePresignedUploadUrl(request, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_업로드는_사용자가_없으면_401_예외를_던진다() {
        FileController controller = new FileController(fileService, userRepository);
        PresignedUploadRequest request = new PresignedUploadRequest(UploadType.TEAM, "team.png", "image/png");
        given(userRepository.findByLoginId("user1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generatePresignedUploadUrl(request, auth()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private TestingAuthenticationToken auth() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("user1", null);
        authentication.setAuthenticated(true);
        return authentication;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
