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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "파일 업로드 API")
public class FileController {

    private static final int ANONYMOUS_ISSUE_LIMIT = 10;
    private static final Duration ANONYMOUS_ISSUE_WINDOW = Duration.ofMinutes(1);

    private final FileService fileService;
    private final UserRepository userRepository;
    private final SimpleRateLimiter rateLimiter;

    @PostMapping("/presigned-upload")
    @Operation(
            summary = "presigned PUT URL 발급",
            description = "클라이언트가 MinIO에 직접 업로드할 수 있는 presigned PUT URL을 발급합니다. " +
                    "PROFILE 타입은 비인증 요청도 허용됩니다(회원가입용). " +
                    "TEAM, PROOF 타입은 로그인 필수입니다. " +
                    "업로드 후 반환된 objectKey를 팀 생성 또는 회원가입 API에 전달하세요."
    )
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> generatePresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        if (request.type() != UploadType.PROFILE) {
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new BusinessException("이미지 업로드는 로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
            }
            String loginId = authentication.getName();
            User user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
            return ResponseEntity.ok(ApiResponse.success(fileService.generatePresignedPutUrl(user.getId(), request)));
        }

        Long userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            userId = userRepository.findByLoginId(authentication.getName())
                    .map(User::getId)
                    .orElse(null);
        }
        if (userId == null) {
            requireAnonymousIssueQuota(httpRequest);
        }
        return ResponseEntity.ok(ApiResponse.success(fileService.generatePresignedPutUrl(userId, request)));
    }

    /**
     * 비인증 PROFILE 발급은 누구나 호출할 수 있으므로 IP 단위로 발급 횟수를 제한한다.
     */
    private void requireAnonymousIssueQuota(HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        if (!rateLimiter.tryAcquire("presigned-upload:" + clientIp, ANONYMOUS_ISSUE_LIMIT, ANONYMOUS_ISSUE_WINDOW)) {
            throw new BusinessException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private String resolveClientIp(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return "unknown";
        }
        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String remoteAddr = httpRequest.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
