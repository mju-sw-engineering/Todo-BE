package com.todo.domain.feed.controller;

import com.todo.domain.feed.dto.response.BadgeResponse;
import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.service.FeedService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    private FeedService feedService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 월간_벌집_현황을_반환한다() {
        FeedController controller = new FeedController(feedService, clock);
        MonthlyHiveResponse serviceResponse = new MonthlyHiveResponse(2026, 8, List.of(1, 2, 3), 5);
        given(feedService.getMonthlyHive("user1", 2026, 8)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<MonthlyHiveResponse>> response =
                controller.getMonthlyHive(2026, 8, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("월간 벌집 현황을 조회했습니다");
    }

    @Test
    void 보관함을_반환한다() {
        FeedController controller = new FeedController(feedService, clock);
        List<HiveArchiveMonthResponse> serviceResponse = List.of(
                new HiveArchiveMonthResponse(2026, 7, 20, 31)
        );
        given(feedService.getHiveArchive("user1", 6)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<List<HiveArchiveMonthResponse>>> response =
                controller.getHiveArchive(6, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("보관함을 조회했습니다");
    }

    @Test
    void 팀_리듬을_반환한다() {
        FeedController controller = new FeedController(feedService, clock);
        List<TeamRhythmResponse> serviceResponse = List.of(
                new TeamRhythmResponse(1L, "팀", 3, 5, List.of(), List.of())
        );
        given(feedService.getTeamRhythm("user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<List<TeamRhythmResponse>>> response =
                controller.getTeamRhythm(auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("팀 리듬을 조회했습니다");
    }

    @Test
    void 뱃지_목록을_반환한다() {
        FeedController controller = new FeedController(feedService, clock);
        List<BadgeResponse> serviceResponse = List.of(
                new BadgeResponse("first-honey", "첫 꿀", "drop", true)
        );
        given(feedService.getBadges("user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<List<BadgeResponse>>> response =
                controller.getBadges(auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("뱃지 목록을 조회했습니다");
    }

    @Test
    void 잔디를_반환한다() {
        FeedController controller = new FeedController(feedService, clock);
        MyStreakResponse serviceResponse = new MyStreakResponse(List.of(), 3);
        given(feedService.getMyStreak("user1", null, null)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyStreakResponse>> response =
                controller.getMyStreak(null, null, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("잔디를 조회했습니다");
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
