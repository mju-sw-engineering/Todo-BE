package com.todo.domain.terms.controller;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.terms.dto.request.ConsentRequest;
import com.todo.domain.terms.dto.response.AllTermsResponse;
import com.todo.domain.terms.dto.response.VersionCheckItem;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

@Tag(name = "Terms", description = "약관 API")
public interface TermsControllerDocs {

    @Operation(summary = "약관 전체 조회", description = "이용약관·개인정보 처리방침·마케팅 수신 동의 현재 버전을 한꺼번에 반환합니다.")
    ResponseEntity<ApiResponse<AllTermsResponse>> getAllTerms();

    @Operation(summary = "내가 동의한 약관 조회", description = "로그인한 사용자가 동의한 버전의 약관 3종을 한꺼번에 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<AllTermsResponse>> getAllAgreedTerms(Authentication authentication);

    @Operation(summary = "약관 재동의", description = "약관 버전 업 시 새 버전에 동의합니다. 이미 동일 버전에 동의했다면 409를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동의 저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 해당 버전에 동의함",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> saveConsent(ConsentRequest request, Authentication authentication);

    @Operation(summary = "약관 버전 비교", description = "동의한 버전과 최신 버전을 비교해 재동의 필요 여부를 약관 타입별로 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "버전 비교 성공")
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Map<ConsentType, VersionCheckItem>>> getVersionCheck(Authentication authentication);
}
