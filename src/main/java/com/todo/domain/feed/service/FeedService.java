package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.MyStreakDayResponse;
import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmMemberResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.dto.response.TeamWeekRhythmResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.repository.CheckInActivityRecord;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.UserActivityRecord;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 피드 화면 집계. "그날 기록을 남겼다"는 투두 생성, 체크인, 제출 세 가지를 뜻하며
 * 하루의 양은 같은 날 손댄 서로 다른 투두 수로 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TEAM_RHYTHM_WEEKS = 8;
    private static final int MY_STREAK_WEEKS = 16;
    /** 연속 일수 계산에 쓰는 조회 범위. 이보다 긴 연속은 이 값에서 멈춘다. */
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final TeamMemberRepository teamMemberRepository;
    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final WorkItemCheckInRepository workItemCheckInRepository;
    private final UserRepository userRepository;

    public List<TeamRhythmResponse> getTeamRhythm(String loginId) {
        User user = findAuthenticatedUser(loginId);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(STREAK_LOOKBACK_DAYS);

        return teamMemberRepository.findTeamsByUserId(user.getId()).stream()
                .map(team -> buildTeamRhythm(team, today, from))
                .toList();
    }

    public MyStreakResponse getMyStreak(String loginId) {
        User user = findAuthenticatedUser(loginId);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(STREAK_LOOKBACK_DAYS);

        Map<LocalDate, Set<Long>> todosByDate = new HashMap<>();
        todoRepository.findCreationActivityByCreatorId(user.getId(), from.atStartOfDay())
                .forEach(r -> add(todosByDate, r.getOccurredAt().toLocalDate(), r.getTodoId()));
        todoWorkItemRepository.findSubmissionActivityByAssigneeId(user.getId(), from.atStartOfDay())
                .forEach(r -> add(todosByDate, r.getOccurredAt().toLocalDate(), r.getTodoId()));
        workItemCheckInRepository.findActivityByUserId(user.getId(), from)
                .forEach(r -> add(todosByDate, r.getOccurredOn(), r.getTodoId()));

        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridStart = thisMonday.minusWeeks(MY_STREAK_WEEKS - 1);
        List<MyStreakDayResponse> days = new ArrayList<>();
        for (int i = 0; i < MY_STREAK_WEEKS * 7; i++) {
            LocalDate date = gridStart.plusDays(i);
            int count = date.isAfter(today) ? 0 : todosByDate.getOrDefault(date, Set.of()).size();
            days.add(MyStreakDayResponse.from(date, count));
        }

        return MyStreakResponse.from(days, countStreak(todosByDate.keySet(), today));
    }

    private TeamRhythmResponse buildTeamRhythm(Team team, LocalDate today, LocalDate from) {
        List<TeamMember> members = teamMemberRepository.findByTeamIdWithUser(team.getId());

        Map<LocalDate, Set<Long>> activeByDate = new HashMap<>();
        todoRepository.findCreationActivityByTeamId(team.getId(), from.atStartOfDay())
                .forEach(r -> add(activeByDate, r.getOccurredAt().toLocalDate(), r.getUserId()));
        todoWorkItemRepository.findSubmissionActivityByTeamId(team.getId(), from.atStartOfDay())
                .forEach(r -> add(activeByDate, r.getOccurredAt().toLocalDate(), r.getUserId()));
        workItemCheckInRepository.findActivityByTeamId(team.getId(), from)
                .forEach(r -> add(activeByDate, r.getOccurredOn(), r.getUserId()));

        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<TeamWeekRhythmResponse> weeks = new ArrayList<>();
        for (int w = TEAM_RHYTHM_WEEKS - 1; w >= 0; w--) {
            LocalDate weekStart = thisMonday.minusWeeks(w);
            List<Integer> counts = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                LocalDate date = weekStart.plusDays(d);
                counts.add(date.isAfter(today)
                        ? null
                        : activeByDate.getOrDefault(date, Set.of()).size());
            }
            weeks.add(TeamWeekRhythmResponse.from(weekStart, counts));
        }

        Set<Long> activeToday = activeByDate.getOrDefault(today, Set.of());
        List<TeamRhythmMemberResponse> todayMembers = members.stream()
                .map(TeamMember::getUser)
                .filter(member -> activeToday.contains(member.getId()))
                .map(TeamRhythmMemberResponse::from)
                .toList();

        return TeamRhythmResponse.from(
                team,
                members.size(),
                countStreak(activeByDate.keySet(), today),
                weeks,
                todayMembers
        );
    }

    /**
     * 오늘부터 거꾸로 센 연속 활동 일수. 오늘 아직 기록이 없어도
     * 어제까지 이어졌다면 끊긴 것으로 보지 않는다.
     */
    private int countStreak(Set<LocalDate> activeDates, LocalDate today) {
        LocalDate cursor = activeDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (activeDates.contains(cursor) && streak < STREAK_LOOKBACK_DAYS) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private void add(Map<LocalDate, Set<Long>> byDate, LocalDate date, Long id) {
        byDate.computeIfAbsent(date, key -> new HashSet<>()).add(id);
    }

    private User findAuthenticatedUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }
}
