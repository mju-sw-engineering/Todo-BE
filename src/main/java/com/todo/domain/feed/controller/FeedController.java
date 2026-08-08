package com.todo.domain.feed.controller;

import com.todo.domain.feed.dto.response.BadgeResponse;
import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.service.FeedService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feed")
public class FeedController implements FeedControllerDocs {

    private final FeedService feedService;
    private final Clock clock;

    @GetMapping("/hive")
    public ResponseEntity<ApiResponse<MonthlyHiveResponse>> getMonthlyHive(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication
    ) {
        LocalDate today = LocalDate.now(clock);
        int targetYear = year != null ? year : today.getYear();
        int targetMonth = month != null ? month : today.getMonthValue();
        MonthlyHiveResponse response =
                feedService.getMonthlyHive(authentication.getName(), targetYear, targetMonth);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/hive/archive")
    public ResponseEntity<ApiResponse<List<HiveArchiveMonthResponse>>> getHiveArchive(
            @RequestParam(defaultValue = "6") int months,
            Authentication authentication
    ) {
        List<HiveArchiveMonthResponse> response =
                feedService.getHiveArchive(authentication.getName(), months);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/team-rhythm")
    public ResponseEntity<ApiResponse<List<TeamRhythmResponse>>> getTeamRhythm(Authentication authentication) {
        String loginId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(feedService.getTeamRhythm(loginId)));
    }

    @GetMapping("/badges")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getBadges(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getBadges(authentication.getName())));
    }

    @GetMapping("/my-streak")
    public ResponseEntity<ApiResponse<MyStreakResponse>> getMyStreak(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(feedService.getMyStreak(loginId, startDate, endDate)));
    }
}
