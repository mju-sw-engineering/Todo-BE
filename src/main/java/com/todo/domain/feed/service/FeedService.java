package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.feed.dto.response.MyStreakDayResponse;
import com.todo.domain.feed.dto.response.MyStreakResponse;
import com.todo.domain.feed.dto.response.TeamRhythmMemberResponse;
import com.todo.domain.feed.dto.response.TeamRhythmResponse;
import com.todo.domain.feed.dto.response.TeamWeekRhythmResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.repository.CheckInActivityRecord;
import com.todo.domain.todo.repository.DailySuccessCount;
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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    /** 잔디 조회 기간 상한 (53주). 무제한 범위 조회를 막는다. */
    private static final int MAX_STREAK_RANGE_DAYS = 53 * 7;
    /** 월간 벌집의 꿀 진하기 상한 (그날 완료 3개 이상 = 3) */
    private static final int MAX_HIVE_LEVEL = 3;
    private static final int MAX_ARCHIVE_MONTHS = 12;

    private final TeamMemberRepository teamMemberRepository;
    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final WorkItemCheckInRepository workItemCheckInRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public List<TeamRhythmResponse> getTeamRhythm(String loginId) {
        User user = findAuthenticatedUser(loginId);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(STREAK_LOOKBACK_DAYS);

        return teamMemberRepository.findTeamsByUserId(user.getId()).stream()
                .map(team -> buildTeamRhythm(team, today, from))
                .toList();
    }

    /**
     * 기간을 생략하면 이번 주를 마지막 줄로 하는 최근 16주를 반환한다.
     * 기간을 주면 시작일은 그 주 월요일로, 종료일은 그 주 일요일로 넓혀
     * 항상 완전한 주 단위 그리드가 되게 한다. 미래 날짜는 count 0으로 내려간다.
     */
    public MyStreakResponse getMyStreak(String loginId, String startDate, String endDate) {
        User user = findAuthenticatedUser(loginId);
        LocalDate today = LocalDate.now(KST);

        LocalDate gridStart;
        LocalDate gridEnd;
        if (startDate == null && endDate == null) {
            LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            gridStart = thisMonday.minusWeeks(MY_STREAK_WEEKS - 1);
            gridEnd = thisMonday.plusDays(6);
        } else {
            LocalDate start = parseRequiredDate("startDate", startDate);
            LocalDate end = parseRequiredDate("endDate", endDate);
            if (start.isAfter(end)) {
                throw new BusinessException("startDate는 endDate보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
            }
            gridStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            gridEnd = end.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (gridStart.plusDays(MAX_STREAK_RANGE_DAYS).isBefore(gridEnd)) {
                throw new BusinessException("조회 기간은 최대 53주까지 가능합니다.", HttpStatus.BAD_REQUEST);
            }
        }

        // 연속 일수는 조회 기간과 무관하게 오늘 기준이므로 항상 최근 1년을 함께 본다.
        LocalDate from = min(gridStart, today.minusDays(STREAK_LOOKBACK_DAYS));

        Map<LocalDate, Set<Long>> todosByDate = new HashMap<>();
        todoRepository.findCreationActivityByCreatorId(user.getId(), from.atStartOfDay())
                .forEach(r -> add(todosByDate, r.getOccurredAt().toLocalDate(), r.getTodoId()));
        todoWorkItemRepository.findSubmissionActivityByAssigneeId(user.getId(), from.atStartOfDay())
                .forEach(r -> add(todosByDate, r.getOccurredAt().toLocalDate(), r.getTodoId()));
        workItemCheckInRepository.findActivityByUserId(user.getId(), from)
                .forEach(r -> add(todosByDate, r.getOccurredOn(), r.getTodoId()));

        List<MyStreakDayResponse> days = new ArrayList<>();
        for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
            int count = date.isAfter(today) ? 0 : todosByDate.getOrDefault(date, Set.of()).size();
            days.add(MyStreakDayResponse.from(date, count));
        }

        return MyStreakResponse.from(days, countStreak(todosByDate.keySet(), today));
    }

    /**
     * 월간 벌집: 하루 = 1칸, 그날 완료(SUCCESS)한 작업 수에 따라 꿀 진하기(0~3).
     * 이번 달이면 오늘 이후 날은 null, 미래 달은 조회할 수 없다.
     */
    public MonthlyHiveResponse getMonthlyHive(String loginId, int year, int month) {
        User user = findAuthenticatedUser(loginId);
        YearMonth target = toYearMonth(year, month);
        LocalDate today = LocalDate.now(clock);
        if (target.isAfter(YearMonth.from(today))) {
            throw new BusinessException("미래 달의 벌집은 조회할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        Map<LocalDate, Long> countByDay = countSuccessByDay(
                user.getId(),
                target.atDay(1).atStartOfDay(),
                target.atEndOfMonth().plusDays(1).atStartOfDay()
        );

        List<Integer> dayLevels = new ArrayList<>();
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            if (date.isAfter(today)) {
                dayLevels.add(null);
                continue;
            }
            dayLevels.add((int) Math.min(countByDay.getOrDefault(date, 0L), MAX_HIVE_LEVEL));
        }

        Set<LocalDate> successDates = countSuccessByDay(
                user.getId(),
                today.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        ).keySet();

        return MonthlyHiveResponse.of(
                target.getYear(), target.getMonthValue(), dayLevels, countStreak(successDates, today));
    }

    /**
     * 벌집 보관함: 이번 달을 제외한 최근 N개월의 (꿀 채운 날 수 / 전체 일수).
     */
    public List<HiveArchiveMonthResponse> getHiveArchive(String loginId, int months) {
        if (months < 1 || months > MAX_ARCHIVE_MONTHS) {
            throw new BusinessException("months는 1~" + MAX_ARCHIVE_MONTHS + " 사이여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        User user = findAuthenticatedUser(loginId);
        YearMonth current = YearMonth.from(LocalDate.now(clock));
        YearMonth from = current.minusMonths(months);

        Map<LocalDate, Long> countByDay = countSuccessByDay(
                user.getId(),
                from.atDay(1).atStartOfDay(),
                current.atDay(1).atStartOfDay()
        );

        List<HiveArchiveMonthResponse> archive = new ArrayList<>();
        for (YearMonth ym = from; ym.isBefore(current); ym = ym.plusMonths(1)) {
            final YearMonth targetMonth = ym;
            int filledDays = (int) countByDay.keySet().stream()
                    .filter(day -> YearMonth.from(day).equals(targetMonth))
                    .count();
            archive.add(HiveArchiveMonthResponse.of(ym.getYear(), ym.getMonthValue(), filledDays, ym.lengthOfMonth()));
        }
        return archive;
    }

    private Map<LocalDate, Long> countSuccessByDay(Long userId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return todoWorkItemRepository.countDailySuccessByAssignee(userId, startInclusive, endExclusive)
                .stream()
                .collect(Collectors.toMap(DailySuccessCount::getDay, DailySuccessCount::getCount, Long::sum));
    }

    private YearMonth toYearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (java.time.DateTimeException e) {
            throw new BusinessException("올바르지 않은 연월입니다.", HttpStatus.BAD_REQUEST);
        }
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

    private LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private LocalDate parseRequiredDate(String parameterName, String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException(parameterName + "는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        try {
            return LocalDate.parse(date);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(parameterName + " 형식이 올바르지 않습니다. (yyyy-MM-dd)", HttpStatus.BAD_REQUEST);
        }
    }

    private User findAuthenticatedUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }
}
