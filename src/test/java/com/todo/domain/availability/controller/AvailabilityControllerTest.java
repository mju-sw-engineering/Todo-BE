package com.todo.domain.availability.controller;

import com.todo.domain.availability.dto.request.CreateAvailabilityPollRequest;
import com.todo.domain.availability.dto.request.SlotItem;
import com.todo.domain.availability.dto.request.SubmitAvailabilityRequest;
import com.todo.domain.availability.dto.response.AvailabilityPollListResponse;
import com.todo.domain.availability.dto.response.AvailabilitySummaryResponse;
import com.todo.domain.availability.service.AvailabilityService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerTest {

    @Mock
    private AvailabilityService availabilityService;

    private Authentication auth(String loginId) {
        Authentication auth = mock(Authentication.class);
        given(auth.getName()).willReturn(loginId);
        return auth;
    }

    @Test
    void 투표_목록_조회_성공() {
        AvailabilityController controller = new AvailabilityController(availabilityService);
        Authentication auth = auth("user1");
        AvailabilityPollListResponse item = new AvailabilityPollListResponse(
                1L, "일정 조율",
                List.of(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5)),
                3L, 1L, true, false
        );
        given(availabilityService.getPolls(10L, "user1")).willReturn(List.of(item));

        ResponseEntity<ApiResponse<List<AvailabilityPollListResponse>>> response =
                controller.getPolls(10L, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).title()).isEqualTo("일정 조율");
    }

    @Test
    void 투표_생성_201_반환() {
        AvailabilityController controller = new AvailabilityController(availabilityService);
        Authentication auth = auth("user1");
        CreateAvailabilityPollRequest request = new CreateAvailabilityPollRequest(
                "새 투표",
                List.of(LocalDate.of(2026, 8, 4)),
                9, 18
        );

        ResponseEntity<ApiResponse<Void>> response = controller.createPoll(10L, request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        then(availabilityService).should().createPoll(10L, "user1", request);
    }

    @Test
    void 가능시간_제출_성공() {
        AvailabilityController controller = new AvailabilityController(availabilityService);
        Authentication auth = auth("user1");
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(
                List.of(new SlotItem(LocalDate.of(2026, 8, 4), 9))
        );

        ResponseEntity<ApiResponse<Void>> response = controller.submitResponse(100L, request, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        then(availabilityService).should().submitResponse(100L, "user1", request);
    }

    @Test
    void 결과_요약_조회_성공() {
        AvailabilityController controller = new AvailabilityController(availabilityService);
        Authentication auth = auth("user1");
        AvailabilitySummaryResponse summary = new AvailabilitySummaryResponse(
                100L, "일정 조율",
                List.of(LocalDate.of(2026, 8, 4)),
                9, 18, 2L, 2L, true,
                List.of(), List.of(), null
        );
        given(availabilityService.getSummary(100L, "user1")).willReturn(summary);

        ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> response =
                controller.getSummary(100L, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().allResponded()).isTrue();
        assertThat(response.getBody().getData().pollId()).isEqualTo(100L);
    }
}
