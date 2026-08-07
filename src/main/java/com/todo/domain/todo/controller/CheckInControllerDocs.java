package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CheckInRequest;
import com.todo.domain.todo.dto.response.CheckInResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "CheckIn", description = "투두 체크인(진행 남기기) API")
public interface CheckInControllerDocs {

    @Operation(
            summary = "체크인 남기기",
            description = "진행 중인 본인 WorkItem에 오늘의 진행 메모를 남깁니다. 같은 항목에는 하루 1번만 가능합니다."
    )
    ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @Parameter(description = "WorkItem ID") Long workItemId,
            CheckInRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "체크인 목록 조회",
            description = "WorkItem에 남긴 체크인 목록을 최신순으로 조회합니다. 팀원이면 누구나 볼 수 있습니다."
    )
    ResponseEntity<ApiResponse<List<CheckInResponse>>> getCheckIns(
            @Parameter(description = "WorkItem ID") Long workItemId,
            Authentication authentication
    );
}
