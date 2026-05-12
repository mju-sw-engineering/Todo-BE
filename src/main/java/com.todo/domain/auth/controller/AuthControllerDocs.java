package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(summary = "회원가입", description = "아이디, 비밀번호, 닉네임, 프로필 이미지(선택)로 계정을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = SignupResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 중복 아이디",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<SignupResponse>> signup(SignupRequest request);

    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하고 JWT 액세스 토큰을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "아이디 또는 비밀번호 오류",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<LoginResponse>> login(LoginRequest request);
}
