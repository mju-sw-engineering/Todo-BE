package com.todo.domain.availability.controller;

import com.todo.domain.availability.dto.request.CreateAvailabilityPollRequest;
import com.todo.domain.availability.dto.request.SubmitAvailabilityRequest;
import com.todo.domain.availability.dto.response.AvailabilityPollListResponse;
import com.todo.domain.availability.dto.response.AvailabilitySummaryResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "Availability", description = "가능 시간 투표 API")
@SecurityRequirement(name = "bearerAuth")
public interface AvailabilityControllerDocs {

    @Operation(summary = "투표 목록 조회", description = "팀의 가능 시간 투표 목록을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀원이 아님",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<List<AvailabilityPollListResponse>>> getPolls(Long teamId, Authentication authentication);

    @Operation(summary = "투표 생성", description = "가능 시간 투표를 생성합니다. 팀원 누구나 생성할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀원이 아님",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<Void>> createPoll(Long teamId, CreateAvailabilityPollRequest request, Authentication authentication);

    @Operation(summary = "가능 시간 제출·수정", description = "내 가능 시간을 제출합니다. 기존 응답이 있으면 전체 교체됩니다. 빈 배열이면 응답 취소.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 날짜 또는 시간",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀원이 아님",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<Void>> submitResponse(Long pollId, SubmitAvailabilityRequest request, Authentication authentication);

    @Operation(summary = "결과 요약 조회", description = "히트맵 집계 및 최적 시간대를 반환합니다. 내 응답 슬롯도 함께 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀원이 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "투표 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> getSummary(Long pollId, Authentication authentication);
}
