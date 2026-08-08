package com.todo.domain.feed.controller;

import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "Feed", description = "피드(월간 벌집·보관함) API")
public interface FeedControllerDocs {

    @Operation(summary = "월간 벌집 조회",
            description = "하루 = 1칸, 그날 완료한 작업 수에 따라 꿀 진하기(0~3)를 반환합니다. "
                    + "이번 달은 오늘 이후 날이 null이며, 미래 달은 조회할 수 없습니다. year/month를 생략하면 이번 달입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MonthlyHiveResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "올바르지 않은 연월 또는 미래 달 조회"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
    })
    ResponseEntity<ApiResponse<MonthlyHiveResponse>> getMonthlyHive(
            @Parameter(description = "연도 (생략 시 올해)", example = "2026") Integer year,
            @Parameter(description = "월 1~12 (생략 시 이번 달)", example = "8") Integer month,
            Authentication authentication
    );

    @Operation(summary = "벌집 보관함 조회",
            description = "이번 달을 제외한 최근 N개월의 (꿀 채운 날 수 / 전체 일수) 목록을 과거 → 최신 순으로 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = HiveArchiveMonthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "months 범위 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
    })
    ResponseEntity<ApiResponse<List<HiveArchiveMonthResponse>>> getHiveArchive(
            @Parameter(description = "조회할 과거 개월 수 (1~12, 기본 6)", example = "6") int months,
            Authentication authentication
    );
}
