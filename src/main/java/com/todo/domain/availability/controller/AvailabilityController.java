package com.todo.domain.availability.controller;

import com.todo.domain.availability.dto.request.CreateAvailabilityPollRequest;
import com.todo.domain.availability.dto.request.SubmitAvailabilityRequest;
import com.todo.domain.availability.dto.response.AvailabilityPollListResponse;
import com.todo.domain.availability.dto.response.AvailabilitySummaryResponse;
import com.todo.domain.availability.service.AvailabilityService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AvailabilityController implements AvailabilityControllerDocs {

    private final AvailabilityService availabilityService;

    @GetMapping("/api/teams/{teamId}/availability-polls")
    public ResponseEntity<ApiResponse<List<AvailabilityPollListResponse>>> getPolls(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(availabilityService.getPolls(teamId, authentication.getName())));
    }

    @PostMapping("/api/teams/{teamId}/availability-polls")
    public ResponseEntity<ApiResponse<Void>> createPoll(
            @PathVariable Long teamId,
            @Valid @RequestBody CreateAvailabilityPollRequest request,
            Authentication authentication
    ) {
        availabilityService.createPoll(teamId, authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @PutMapping("/api/availability-polls/{pollId}/responses/me")
    public ResponseEntity<ApiResponse<Void>> submitResponse(
            @PathVariable Long pollId,
            @Valid @RequestBody SubmitAvailabilityRequest request,
            Authentication authentication
    ) {
        availabilityService.submitResponse(pollId, authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/availability-polls/{pollId}/summary")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> getSummary(
            @PathVariable Long pollId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(availabilityService.getSummary(pollId, authentication.getName())));
    }
}
