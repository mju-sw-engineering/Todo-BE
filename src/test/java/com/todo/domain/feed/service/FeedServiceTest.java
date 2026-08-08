package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.MyStreakDayResponse;
import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.dto.response.TeamWeekRhythmResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.repository.CheckInActivityRecord;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.UserActivityRecord;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long TEAM_ID = 1L;

    @InjectMocks
    private FeedService feedService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Mock
    private WorkItemCheckInRepository workItemCheckInRepository;

    @Mock
    private UserRepository userRepository;

    private record Activity(LocalDateTime occurredAt, Long userId, Long todoId) implements UserActivityRecord {
        public LocalDateTime getOccurredAt() {
            return occurredAt;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getTodoId() {
            return todoId;
        }
    }

    private record CheckInActivity(LocalDate occurredOn, Long userId, Long todoId) implements CheckInActivityRecord {
        public LocalDate getOccurredOn() {
            return occurredOn;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getTodoId() {
            return todoId;
        }
    }

    @Test
    void 팀_리듬은_8주치_주간_데이터를_반환한다() {
        User me = user(1L);
        givenMyTeams(me);
        givenTeamActivity(List.of(), List.of(), List.of());

        List<TeamRhythmResponse> result = feedService.getTeamRhythm("1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weeks()).hasSize(8);
        LocalDate thisMonday = LocalDate.now(KST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(result.get(0).weeks().get(7).startDate()).isEqualTo(thisMonday);
        assertThat(result.get(0).weeks().get(0).startDate()).isEqualTo(thisMonday.minusWeeks(7));
    }

    @Test
    void 같은_날_여러_기록을_남긴_팀원은_한_명으로_센다() {
        User me = user(1L);
        givenMyTeams(me);
        LocalDate today = LocalDate.now(KST);
        givenTeamActivity(
                List.of(new Activity(today.atTime(9, 0), 1L, 100L)),
                List.of(new Activity(today.atTime(18, 0), 1L, 100L)),
                List.of(new CheckInActivity(today, 2L, 101L))
        );

        List<TeamRhythmResponse> result = feedService.getTeamRhythm("1");

        TeamWeekRhythmResponse currentWeek = result.get(0).weeks().get(7);
        int todayIndex = today.getDayOfWeek().getValue() - 1;
        assertThat(currentWeek.counts().get(todayIndex)).isEqualTo(2);
    }

    @Test
    void 아직_오지_않은_요일은_null이다() {
        User me = user(1L);
        givenMyTeams(me);
        givenTeamActivity(List.of(), List.of(), List.of());

        List<TeamRhythmResponse> result = feedService.getTeamRhythm("1");

        TeamWeekRhythmResponse currentWeek = result.get(0).weeks().get(7);
        int todayIndex = LocalDate.now(KST).getDayOfWeek().getValue() - 1;
        for (int d = 0; d < 7; d++) {
            if (d <= todayIndex) {
                assertThat(currentWeek.counts().get(d)).isNotNull();
            } else {
                assertThat(currentWeek.counts().get(d)).isNull();
            }
        }
    }

    @Test
    void 오늘_기록을_남긴_팀원만_todayMembers에_담는다() {
        User me = user(1L);
        givenMyTeams(me);
        LocalDate today = LocalDate.now(KST);
        givenTeamActivity(
                List.of(new Activity(today.atTime(9, 0), 2L, 100L)),
                List.of(),
                List.of()
        );

        List<TeamRhythmResponse> result = feedService.getTeamRhythm("1");

        assertThat(result.get(0).todayMembers())
                .extracting(m -> m.userId())
                .containsExactly(2L);
    }

    @Test
    void 어제까지_이어진_연속은_오늘_기록이_없어도_유지된다() {
        User me = user(1L);
        givenMyTeams(me);
        LocalDate today = LocalDate.now(KST);
        givenTeamActivity(
                List.of(
                        new Activity(today.minusDays(1).atTime(9, 0), 1L, 100L),
                        new Activity(today.minusDays(2).atTime(9, 0), 1L, 101L),
                        new Activity(today.minusDays(4).atTime(9, 0), 1L, 102L)
                ),
                List.of(),
                List.of()
        );

        List<TeamRhythmResponse> result = feedService.getTeamRhythm("1");

        assertThat(result.get(0).streakDays()).isEqualTo(2);
    }

    @Test
    void 나의_잔디는_월요일_시작_112일을_반환한다() {
        givenMe(user(1L));
        givenMyActivity(List.of(), List.of(), List.of());

        MyStreakResponse result = feedService.getMyStreak("1", null, null);

        assertThat(result.days()).hasSize(112);
        assertThat(result.days().get(0).date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        LocalDate thisMonday = LocalDate.now(KST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(result.days().get(111).date()).isEqualTo(thisMonday.plusDays(6));
    }

    @Test
    void 하루의_기록_수는_손댄_서로_다른_투두_수로_센다() {
        givenMe(user(1L));
        LocalDate today = LocalDate.now(KST);
        givenMyActivity(
                List.of(new Activity(today.atTime(9, 0), 1L, 100L)),
                List.of(new Activity(today.atTime(10, 0), 1L, 100L)),
                List.of(new CheckInActivity(today, 1L, 101L))
        );

        MyStreakResponse result = feedService.getMyStreak("1", null, null);

        MyStreakDayResponse todayEntry = result.days().stream()
                .filter(d -> d.date().equals(today))
                .findFirst()
                .orElseThrow();
        assertThat(todayEntry.count()).isEqualTo(2);
    }

    @Test
    void 나의_연속_일수를_계산한다() {
        givenMe(user(1L));
        LocalDate today = LocalDate.now(KST);
        givenMyActivity(
                List.of(
                        new Activity(today.atTime(9, 0), 1L, 100L),
                        new Activity(today.minusDays(1).atTime(9, 0), 1L, 101L),
                        new Activity(today.minusDays(3).atTime(9, 0), 1L, 102L)
                ),
                List.of(),
                List.of()
        );

        MyStreakResponse result = feedService.getMyStreak("1", null, null);

        assertThat(result.currentStreak()).isEqualTo(2);
    }

    @Test
    void 기간을_지정하면_월요일에서_일요일까지_완전한_주로_넓혀_반환한다() {
        givenMe(user(1L));
        givenMyActivity(List.of(), List.of(), List.of());

        // 2026-01-01은 목요일, 2026-06-30은 화요일
        MyStreakResponse result = feedService.getMyStreak("1", "2026-01-01", "2026-06-30");

        assertThat(result.days().get(0).date()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(result.days().get(0).date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.days().get(result.days().size() - 1).date()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(result.days().get(result.days().size() - 1).date().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(result.days().size() % 7).isZero();
    }

    @Test
    void 기간에_포함된_미래_날짜는_count_0으로_내려간다() {
        givenMe(user(1L));
        givenMyActivity(List.of(), List.of(), List.of());
        LocalDate today = LocalDate.now(KST);

        MyStreakResponse result = feedService.getMyStreak(
                "1", today.toString(), today.plusDays(10).toString());

        assertThat(result.days())
                .filteredOn(d -> d.date().isAfter(today))
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.count()).isZero());
    }

    @Test
    void 시작일이_종료일보다_늦으면_거부한다() {
        givenMe(user(1L));

        assertThatThrownBy(() -> feedService.getMyStreak("1", "2026-06-30", "2026-01-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 시작일만_주면_거부한다() {
        givenMe(user(1L));

        assertThatThrownBy(() -> feedService.getMyStreak("1", "2026-01-01", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 잘못된_날짜_형식은_거부한다() {
        givenMe(user(1L));

        assertThatThrownBy(() -> feedService.getMyStreak("1", "2026/01/01", "2026-06-30"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 오십삼주를_넘는_기간은_거부한다() {
        givenMe(user(1L));

        assertThatThrownBy(() -> feedService.getMyStreak("1", "2025-01-01", "2026-06-30"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void givenMe(User me) {
        given(userRepository.findById(1L)).willReturn(Optional.of(me));
    }

    private void givenMyTeams(User me) {
        givenMe(me);
        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        TeamMember member1 = TeamMember.create(team, me, TeamMemberRole.LEADER);
        TeamMember member2 = TeamMember.create(team, user(2L), TeamMemberRole.MEMBER);
        given(teamMemberRepository.findTeamsByUserId(me.getId())).willReturn(List.of(team));
        given(teamMemberRepository.findByTeamIdWithUser(TEAM_ID)).willReturn(List.of(member1, member2));
    }

    private void givenTeamActivity(
            List<UserActivityRecord> creations,
            List<UserActivityRecord> submissions,
            List<CheckInActivityRecord> checkIns
    ) {
        given(todoRepository.findCreationActivityByTeamId(eq(TEAM_ID), any())).willReturn(creations);
        given(todoWorkItemRepository.findSubmissionActivityByTeamId(eq(TEAM_ID), any())).willReturn(submissions);
        given(workItemCheckInRepository.findActivityByTeamId(eq(TEAM_ID), any())).willReturn(checkIns);
    }

    private void givenMyActivity(
            List<UserActivityRecord> creations,
            List<UserActivityRecord> submissions,
            List<CheckInActivityRecord> checkIns
    ) {
        given(todoRepository.findCreationActivityByCreatorId(anyLong(), any())).willReturn(creations);
        given(todoWorkItemRepository.findSubmissionActivityByAssigneeId(anyLong(), any())).willReturn(submissions);
        given(workItemCheckInRepository.findActivityByUserId(anyLong(), any())).willReturn(checkIns);
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "닉네임" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
