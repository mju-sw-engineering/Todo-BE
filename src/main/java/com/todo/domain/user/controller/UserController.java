package com.todo.domain.user.controller;

import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.service.UserService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(Authentication authentication) {
        String loginId = authentication.getName();
        MyPageResponse response = userService.getMyPage(loginId);
        return ResponseEntity.ok(ApiResponse.success(response, "내 정보를 조회했습니다"));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<MyPageResponse>> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        MyPageResponse response = userService.updateNickname(loginId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "닉네임이 변경되었습니다"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Valid @RequestBody DeleteUserRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        userService.deleteUser(loginId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "회원 탈퇴가 완료되었습니다"));
    }
}
