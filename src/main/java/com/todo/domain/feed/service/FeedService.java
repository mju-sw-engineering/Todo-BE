package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.todo.repository.DailySuccessCount;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    /** 스트릭 계산 시 조회하는 최대 과거 일수 (1년 + 여유) */
    private static final int STREAK_LOOKBACK_DAYS = 400;
    private static final int MAX_LEVEL = 3;
    private static final int MAX_ARCHIVE_MONTHS = 12;

    private final UserRepository userRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final Clock clock;

    /**
     * 월간 벌집: 하루 = 1칸, 그날 완료한 작업 수에 따라 꿀 진하기(0~3).
     * 이번 달이면 오늘 이후 날은 null, 미래 달은 조회할 수 없다.
     */
    public MonthlyHiveResponse getMonthlyHive(String loginId, int year, int month) {
        User user = findUser(loginId);
        YearMonth target = toYearMonth(year, month);
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        if (target.isAfter(current)) {
            throw new BusinessException("미래 달의 벌집은 조회할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        Map<LocalDate, Long> countByDay = countByDay(
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
            long count = countByDay.getOrDefault(date, 0L);
            dayLevels.add((int) Math.min(count, MAX_LEVEL));
        }

        return MonthlyHiveResponse.of(target.getYear(), target.getMonthValue(), dayLevels, calculateStreak(user.getId(), today));
    }

    /**
     * 벌집 보관함: 이번 달을 제외한 최근 N개월의 (꿀 채운 날 수 / 전체 일수).
     */
    public List<HiveArchiveMonthResponse> getHiveArchive(String loginId, int months) {
        if (months < 1 || months > MAX_ARCHIVE_MONTHS) {
            throw new BusinessException("months는 1~" + MAX_ARCHIVE_MONTHS + " 사이여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        User user = findUser(loginId);
        YearMonth current = YearMonth.from(LocalDate.now(clock));
        YearMonth from = current.minusMonths(months);

        Map<LocalDate, Long> countByDay = countByDay(
                user.getId(),
                from.atDay(1).atStartOfDay(),
                current.atDay(1).atStartOfDay()
        );

        List<HiveArchiveMonthResponse> archive = new ArrayList<>();
        for (YearMonth ym = from; ym.isBefore(current); ym = ym.plusMonths(1)) {
            final YearMonth month0 = ym;
            int filledDays = (int) countByDay.keySet().stream()
                    .filter(day -> YearMonth.from(day).equals(month0))
                    .count();
            archive.add(HiveArchiveMonthResponse.of(ym.getYear(), ym.getMonthValue(), filledDays, ym.lengthOfMonth()));
        }
        return archive;
    }

    /**
     * 오늘부터 거꾸로 세는 연속 채움 일수.
     * 오늘 아직 완료가 없으면 어제까지 이어진 스트릭을 반환한다(오늘 실패로 치지 않음).
     */
    private int calculateStreak(Long userId, LocalDate today) {
        Map<LocalDate, Long> countByDay = countByDay(
                userId,
                today.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        LocalDate cursor = countByDay.containsKey(today) ? today : today.minusDays(1);
        int streak = 0;
        while (streak <= STREAK_LOOKBACK_DAYS && countByDay.containsKey(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private Map<LocalDate, Long> countByDay(Long userId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return todoWorkItemRepository.countDailySuccessByAssignee(userId, startInclusive, endExclusive)
                .stream()
                .collect(Collectors.toMap(DailySuccessCount::getDay, DailySuccessCount::getCount, Long::sum));
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND));
    }

    private YearMonth toYearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (java.time.DateTimeException e) {
            throw new BusinessException("올바르지 않은 연월입니다.", HttpStatus.BAD_REQUEST);
        }
    }
}
