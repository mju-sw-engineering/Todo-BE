package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.repository.CheckInActivityRecord;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.UserActivityRecord;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * 월간 벌집·보관함 조회 테스트. 꿀 채움은 잔디와 같은 활동 기준
 * (그날 투두 생성·체크인·제출 중 하나라도)이다. 시간 고정이 필요해 Clock을 직접 주입한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedServiceHiveTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 테스트 기준 오늘: 2026-08-08 (8월은 31일) */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);

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

    private FeedService feedService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atTime(12, 0).atZone(SEOUL).toInstant(), SEOUL);
        feedService = new FeedService(
                teamMemberRepository,
                todoRepository,
                todoWorkItemRepository,
                workItemCheckInRepository,
                userRepository,
                fixedClock
        );

        User user = User.create("1", "encoded-password", "테스터", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        // months 범위 검증처럼 사용자 조회 전에 끝나는 테스트도 있어 lenient로 둔다
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        givenActivities(List.of(), List.of(), List.of());
    }

    private record Submission(LocalDateTime occurredAt, Long userId, Long todoId)
            implements UserActivityRecord {
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

    private record CheckIn(LocalDate occurredOn, Long userId, Long todoId)
            implements CheckInActivityRecord {
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

    private static UserActivityRecord submitted(LocalDate day, long todoId) {
        return new Submission(day.atTime(15, 0), 1L, todoId);
    }

    private static CheckInActivityRecord checkedIn(LocalDate day, long todoId) {
        return new CheckIn(day, 1L, todoId);
    }

    private void givenActivities(
            List<UserActivityRecord> creations,
            List<UserActivityRecord> submissions,
            List<CheckInActivityRecord> checkIns
    ) {
        lenient().when(todoRepository.findCreationActivityByCreatorId(eq(1L), any()))
                .thenReturn(creations);
        lenient().when(todoWorkItemRepository.findSubmissionActivityByAssigneeId(eq(1L), any()))
                .thenReturn(submissions);
        lenient().when(workItemCheckInRepository.findActivityByUserId(eq(1L), any()))
                .thenReturn(checkIns);
    }

    @Test
    @DisplayName("꿀 진하기는 그날 손댄 서로 다른 투두 수이고 오늘 이후는 null이다")
    void 활동_기준_레벨과_미래_null() {
        LocalDate day1 = LocalDate.of(2026, 8, 1);
        LocalDate day2 = LocalDate.of(2026, 8, 2);
        givenActivities(
                List.of(submitted(day2, 10L)),
                // 8/1: 투두 3개 제출, 8/2: 같은 투두(10)를 생성·체크인해도 1개로 센다
                List.of(submitted(day1, 1L), submitted(day1, 2L), submitted(day1, 3L)),
                List.of(checkedIn(day2, 10L))
        );

        MonthlyHiveResponse response = feedService.getMonthlyHive("1", 2026, 8);

        assertThat(response.dayLevels()).hasSize(31);
        assertThat(response.dayLevels().get(0)).isEqualTo(3);
        // 같은 투두를 여러 방식으로 손대도 하루 1개로 집계
        assertThat(response.dayLevels().get(1)).isEqualTo(1);
        // 활동 없는 지난 날은 0
        assertThat(response.dayLevels().get(2)).isZero();
        // 오늘(8일, index 7)까지는 값, 이후는 null
        assertThat(response.dayLevels().get(7)).isNotNull();
        assertThat(response.dayLevels().get(8)).isNull();
        assertThat(response.dayLevels().get(30)).isNull();
    }

    @Test
    @DisplayName("체크인만 남긴 날도 꿀이 찬다 — 오래 걸리는 투두의 중간 기록 인정")
    void 체크인만으로_꿀_채움() {
        givenActivities(List.of(), List.of(), List.of(checkedIn(LocalDate.of(2026, 8, 5), 7L)));

        MonthlyHiveResponse response = feedService.getMonthlyHive("1", 2026, 8);

        assertThat(response.dayLevels().get(4)).isEqualTo(1);
    }

    @Test
    @DisplayName("오늘 활동이 있으면 오늘을 포함해 연속 일수를 센다")
    void 스트릭_오늘_포함() {
        givenActivities(
                List.of(),
                List.of(submitted(TODAY, 1L), submitted(TODAY.minusDays(1), 2L)),
                // 2일 전은 체크인으로 이어짐, 3일 전은 공백 → 스트릭 3에서 끊김
                List.of(checkedIn(TODAY.minusDays(2), 3L), checkedIn(TODAY.minusDays(4), 4L))
        );

        MonthlyHiveResponse response = feedService.getMonthlyHive("1", 2026, 8);

        assertThat(response.currentStreak()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘 아직 활동이 없으면 어제까지 이어진 스트릭을 반환한다")
    void 스트릭_오늘_미활동이면_어제부터() {
        givenActivities(
                List.of(),
                List.of(submitted(TODAY.minusDays(1), 1L), submitted(TODAY.minusDays(2), 2L)),
                List.of()
        );

        MonthlyHiveResponse response = feedService.getMonthlyHive("1", 2026, 8);

        assertThat(response.currentStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("과거 달은 모든 날에 값이 있고 null이 없다")
    void 과거달_null_없음() {
        givenActivities(List.of(), List.of(submitted(LocalDate.of(2026, 7, 15), 1L)), List.of());

        MonthlyHiveResponse response = feedService.getMonthlyHive("1", 2026, 7);

        assertThat(response.dayLevels()).hasSize(31);
        assertThat(response.dayLevels()).doesNotContainNull();
        assertThat(response.dayLevels().get(14)).isEqualTo(1);
    }

    @Test
    @DisplayName("미래 달 조회는 400 예외를 던진다")
    void 미래달_조회_거부() {
        assertThatThrownBy(() -> feedService.getMonthlyHive("1", 2026, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("미래 달");
    }

    @Test
    @DisplayName("올바르지 않은 월은 400 예외를 던진다")
    void 잘못된_연월_거부() {
        assertThatThrownBy(() -> feedService.getMonthlyHive("1", 2026, 13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("올바르지 않은 연월");
    }

    @Test
    @DisplayName("보관함은 이번 달을 제외한 최근 N개월을 과거부터 순서대로 반환한다")
    void 보관함_월별_집계() {
        givenActivities(
                List.of(),
                List.of(submitted(LocalDate.of(2026, 6, 1), 1L), submitted(LocalDate.of(2026, 7, 10), 2L)),
                List.of(checkedIn(LocalDate.of(2026, 6, 2), 3L))
        );

        List<HiveArchiveMonthResponse> archive = feedService.getHiveArchive("1", 3);

        assertThat(archive).hasSize(3);
        assertThat(archive.get(0).month()).isEqualTo(5);
        assertThat(archive.get(0).filledDays()).isZero();
        assertThat(archive.get(1).month()).isEqualTo(6);
        assertThat(archive.get(1).filledDays()).isEqualTo(2);
        assertThat(archive.get(1).totalDays()).isEqualTo(30);
        assertThat(archive.get(2).month()).isEqualTo(7);
        assertThat(archive.get(2).filledDays()).isEqualTo(1);
        // 이번 달(8월)은 포함하지 않는다
        assertThat(archive).noneMatch(m -> m.month() == 8);
    }

    @Test
    @DisplayName("months가 범위를 벗어나면 400 예외를 던진다")
    void 보관함_범위_검증() {
        assertThatThrownBy(() -> feedService.getHiveArchive("1", 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> feedService.getHiveArchive("1", 13))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 인증 예외를 던진다")
    void 없는_사용자_거부() {
        lenient().when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.getMonthlyHive("999", 2026, 8))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
