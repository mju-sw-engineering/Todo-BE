package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CheckInRequest;
import com.todo.domain.todo.dto.response.CheckInResponse;
import com.todo.domain.todo.service.WorkItemCheckInService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

    @Mock
    private WorkItemCheckInService workItemCheckInService;

    @Test
    void 체크인_등록_후_응답을_반환한다() {
        CheckInController controller = new CheckInController(workItemCheckInService);
        CheckInRequest request = new CheckInRequest("3장 초안까지 정리했어요");
        CheckInResponse serviceResponse = new CheckInResponse(1L, 2L, "닉네임", LocalDate.of(2026, 8, 8), "3장 초안까지 정리했어요");
        given(workItemCheckInService.checkIn("user1", 10L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<CheckInResponse>> response = controller.checkIn(10L, request, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getMessage()).isEqualTo("체크인이 등록되었습니다.");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 체크인_목록을_반환한다() {
        CheckInController controller = new CheckInController(workItemCheckInService);
        List<CheckInResponse> serviceResponse = List.of(
                new CheckInResponse(1L, 2L, "닉네임", LocalDate.of(2026, 8, 8), "메모")
        );
        given(workItemCheckInService.getCheckIns("user1", 10L)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<List<CheckInResponse>>> response = controller.getCheckIns(10L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("체크인 목록을 조회했습니다");
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
