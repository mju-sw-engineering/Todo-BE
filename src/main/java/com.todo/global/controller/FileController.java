package com.todo.global.controller;

import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import com.todo.global.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "파일 업로드 API")
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @PostMapping("/presigned-upload")
    @Operation(
            summary = "presigned PUT URL 발급",
            description = "클라이언트가 MinIO에 직접 업로드할 수 있는 presigned PUT URL을 발급합니다. " +
                    "업로드 후 반환된 objectKey를 팀 생성 또는 회원가입 API에 전달하세요."
    )
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> generatePresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(fileService.generatePresignedPutUrl(user.getId(), request)));
    }
}
