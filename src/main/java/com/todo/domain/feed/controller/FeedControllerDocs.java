package com.todo.domain.feed.controller;

import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "Feed", description = "피드 화면 집계 API")
public interface FeedControllerDocs {

    @Operation(
            summary = "팀 리듬 조회",
            description = "내가 속한 모든 팀의 최근 8주 요일별 참여 팀원 수, 오늘 참여자, 연속 활동 일수를 반환합니다."
    )
    ResponseEntity<ApiResponse<List<TeamRhythmResponse>>> getTeamRhythm(Authentication authentication);

    @Operation(
            summary = "나의 꾸준함 조회",
            description = "월요일 시작 16주(112일)의 날짜별 기록 수와 현재 연속 일수를 반환합니다. 기록 수는 그날 손댄 서로 다른 투두 수입니다."
    )
    ResponseEntity<ApiResponse<MyStreakResponse>> getMyStreak(Authentication authentication);
}
