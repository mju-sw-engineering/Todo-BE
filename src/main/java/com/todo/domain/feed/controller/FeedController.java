package com.todo.domain.feed.controller;

import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.service.FeedService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feed")
public class FeedController implements FeedControllerDocs {

    private final FeedService feedService;

    @GetMapping("/team-rhythm")
    public ResponseEntity<ApiResponse<List<TeamRhythmResponse>>> getTeamRhythm(Authentication authentication) {
        String loginId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(feedService.getTeamRhythm(loginId)));
    }

    @GetMapping("/my-streak")
    public ResponseEntity<ApiResponse<MyStreakResponse>> getMyStreak(Authentication authentication) {
        String loginId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(feedService.getMyStreak(loginId)));
    }
}
