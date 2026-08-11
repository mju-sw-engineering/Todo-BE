package com.todo.domain.user.controller;

import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.dto.response.UserProfileResponse;
import com.todo.domain.user.service.UserService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Test
    void 마이페이지_응답을_반환한다() {
        UserController controller = new UserController(userService);
        MyPageResponse myPageResponse = new MyPageResponse(1L, "user1", "닉네임", null, List.of());
        given(userService.getMyPage("user1")).willReturn(myPageResponse);

        ResponseEntity<ApiResponse<MyPageResponse>> response = controller.getMyPage(auth());

        assertThat(response.getBody().getData()).isEqualTo(myPageResponse);
    }

    @Test
    void 프로필_조회_응답을_반환한다() {
        UserController controller = new UserController(userService);
        UserProfileResponse profileResponse = new UserProfileResponse(1L, "user1", "닉네임", null);
        given(userService.getMyProfile("user1")).willReturn(profileResponse);

        ResponseEntity<ApiResponse<UserProfileResponse>> response = controller.getMyProfile(auth());

        assertThat(response.getBody().getData()).isEqualTo(profileResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("내 프로필을 조회했습니다");
    }

    @Test
    void 닉네임_수정_응답을_반환한다() {
        UserController controller = new UserController(userService);
        UpdateNicknameRequest request = new UpdateNicknameRequest("새닉네임");
        MyPageResponse myPageResponse = new MyPageResponse(1L, "user1", "새닉네임", null, List.of());
        given(userService.updateNickname("user1", request)).willReturn(myPageResponse);

        ResponseEntity<ApiResponse<MyPageResponse>> response = controller.updateNickname(request, auth());

        assertThat(response.getBody().getData()).isEqualTo(myPageResponse);
    }

    @Test
    void 회원탈퇴_응답을_반환하고_재인증_토큰을_서비스에_전달한다() {
        UserController controller = new UserController(userService);
        DeleteUserRequest request = new DeleteUserRequest("reauth-token");

        ResponseEntity<ApiResponse<Void>> response = controller.deleteUser(request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("회원 탈퇴가 완료되었습니다");
        then(userService).should().deleteUser("user1", request);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
