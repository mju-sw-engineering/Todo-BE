package com.todo.global.controller;

import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.dto.UploadType;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.ClientIpResolver;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "파일 업로드 API")
public class FileController {

    /** 가입 토큰으로 신원이 확인된 요청. 한 명이 사진을 몇 번 고쳐 올려도 넉넉한 수준. */
    private static final int SIGNUP_TOKEN_ISSUE_LIMIT = 5;
    /** 토큰조차 없는 요청. 공용 IP 뒤에 여러 명이 있을 수 있어 조금 더 여유를 둔다. */
    private static final int ANONYMOUS_ISSUE_LIMIT = 10;
    private static final Duration ANONYMOUS_ISSUE_WINDOW = Duration.ofMinutes(1);

    private final FileService fileService;
    private final UserRepository userRepository;
    private final SimpleRateLimiter rateLimiter;
    private final EmailVerificationService emailVerificationService;
    private final JwtUtil jwtUtil;
    private final ClientIpResolver clientIpResolver;

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
            User user = resolveAuthenticatedUser(authentication)
                    .orElseThrow(() -> new BusinessException("이미지 업로드는 로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
            return ResponseEntity.ok(ApiResponse.success(fileService.generatePresignedPutUrl(user.getId(), request)));
        }

        Long userId = resolveAuthenticatedUser(authentication).map(User::getId).orElse(null);
        if (userId == null) {
            requireAnonymousIssueQuota(request.signupToken(), httpRequest);
        }
        return ResponseEntity.ok(ApiResponse.success(fileService.generatePresignedPutUrl(userId, request)));
    }

    /** principal 이름은 userId 숫자다. 익명 principal("anonymousUser") 등 숫자가 아니면 비로그인으로 본다. */
    private Optional<User> resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(Long.parseLong(authentication.getName()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * 비인증 PROFILE 발급은 누구나 호출할 수 있으므로 발급 횟수를 제한한다.
     *
     * 가입 토큰이 오면 그 토큰이 가리키는 이메일 단위로 센다. IP만으로 세면 학교처럼 NAT 뒤에
     * 수백 명이 같은 공인 IP를 쓰는 환경에서 한 버킷을 나눠 쓰게 되어, 한 강의실에서 동시에
     * 가입할 때 뒷사람부터 429를 맞는다.
     */
    private void requireAnonymousIssueQuota(String signupToken, HttpServletRequest httpRequest) {
        String signupEmail = resolveSignupEmail(signupToken);
        if (signupEmail != null) {
            requireQuota("presigned-upload:signup:" + signupEmail, SIGNUP_TOKEN_ISSUE_LIMIT);
            return;
        }
        requireQuota("presigned-upload:ip:" + clientIpResolver.resolve(httpRequest), ANONYMOUS_ISSUE_LIMIT);
    }

    private void requireQuota(String key, int limit) {
        if (!rateLimiter.tryAcquire(key, limit, ANONYMOUS_ISSUE_WINDOW)) {
            throw new BusinessException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 가입 절차에서 발급된 토큰의 이메일을 꺼낸다. 이메일 가입은 이메일 인증 토큰,
     * 애플 가입은 setup token을 쓴다. 어느 쪽도 아니면 null을 돌려 IP 기준으로 폴백한다.
     *
     * 토큰은 신원 식별에만 쓰고 소비하지 않는다. 여기서 소비하면 뒤이은 회원가입이 실패한다.
     */
    private String resolveSignupEmail(String signupToken) {
        if (signupToken == null || signupToken.isBlank()) {
            return null;
        }
        Optional<String> verifiedEmail = emailVerificationService.findVerifiedEmail(signupToken);
        if (verifiedEmail.isPresent()) {
            return verifiedEmail.get();
        }
        try {
            return jwtUtil.parseSetupToken(signupToken).get("email", String.class);
        } catch (Exception e) {
            // 위조·만료된 토큰은 신원으로 인정하지 않고 IP 기준으로 제한한다.
            return null;
        }
    }

}
