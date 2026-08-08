package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.HiveArchiveMonthResponse;
import com.todo.domain.feed.dto.response.MonthlyHiveResponse;
import com.todo.domain.todo.repository.DailySuccessCount;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 테스트 기준 오늘: 2026-08-08 (8월은 31일) */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    private FeedService feedService;

    private User user;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                TODAY.atTime(12, 0).atZone(SEOUL).toInstant(), SEOUL);
        feedService = new FeedService(userRepository, todoWorkItemRepository, fixedClock);

        user = User.create("tester", "encoded-password", "테스터", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        // months 범위 검증처럼 사용자 조회 전에 끝나는 테스트도 있어 lenient로 둔다
        lenient().when(userRepository.findByLoginId("tester")).thenReturn(Optional.of(user));
    }

    private static DailySuccessCount dayCount(LocalDate day, long count) {
        return new DailySuccessCount() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    @Test
    @DisplayName("이번 달 벌집은 완료 개수에 따라 꿀 진하기를 매기고 오늘 이후는 null이다")
    void 이번달_벌집_레벨과_미래_null() {
        given(todoWorkItemRepository.countDailySuccessByAssignee(eq(1L), any(), any()))
                .willReturn(List.of(
                        dayCount(LocalDate.of(2026, 8, 1), 1),
                        dayCount(LocalDate.of(2026, 8, 2), 2),
                        dayCount(LocalDate.of(2026, 8, 3), 5)
                ));

        MonthlyHiveResponse response = feedService.getMonthlyHive("tester", 2026, 8);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(8);
        assertThat(response.dayLevels()).hasSize(31);
        assertThat(response.dayLevels().get(0)).isEqualTo(1);
        assertThat(response.dayLevels().get(1)).isEqualTo(2);
        // 3개 이상은 최대 레벨 3으로 캡
        assertThat(response.dayLevels().get(2)).isEqualTo(3);
        // 완료 없는 지난 날은 0
        assertThat(response.dayLevels().get(3)).isZero();
        // 오늘(8일, index 7)까지는 값, 이후는 null
        assertThat(response.dayLevels().get(7)).isNotNull();
        assertThat(response.dayLevels().get(8)).isNull();
        assertThat(response.dayLevels().get(30)).isNull();
    }

    @Test
    @DisplayName("오늘 완료가 있으면 오늘을 포함해 연속 일수를 센다")
    void 스트릭_오늘_포함() {
        given(todoWorkItemRepository.countDailySuccessByAssignee(eq(1L), any(), any()))
                .willReturn(List.of(
                        dayCount(TODAY, 1),
                        dayCount(TODAY.minusDays(1), 2),
                        dayCount(TODAY.minusDays(2), 1),
                        // 3일 전은 비어 있음 → 스트릭 끊김
                        dayCount(TODAY.minusDays(4), 1)
                ));

        MonthlyHiveResponse response = feedService.getMonthlyHive("tester", 2026, 8);

        assertThat(response.currentStreak()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘 아직 완료가 없으면 어제까지 이어진 스트릭을 반환한다")
    void 스트릭_오늘_미완료면_어제부터() {
        given(todoWorkItemRepository.countDailySuccessByAssignee(eq(1L), any(), any()))
                .willReturn(List.of(
                        dayCount(TODAY.minusDays(1), 1),
                        dayCount(TODAY.minusDays(2), 3)
                ));

        MonthlyHiveResponse response = feedService.getMonthlyHive("tester", 2026, 8);

        assertThat(response.currentStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("과거 달은 모든 날에 값이 있고 null이 없다")
    void 과거달_null_없음() {
        given(todoWorkItemRepository.countDailySuccessByAssignee(eq(1L), any(), any()))
                .willReturn(List.of(dayCount(LocalDate.of(2026, 7, 15), 2)));

        MonthlyHiveResponse response = feedService.getMonthlyHive("tester", 2026, 7);

        assertThat(response.dayLevels()).hasSize(31);
        assertThat(response.dayLevels()).doesNotContainNull();
        assertThat(response.dayLevels().get(14)).isEqualTo(2);
    }

    @Test
    @DisplayName("미래 달 조회는 400 예외를 던진다")
    void 미래달_조회_거부() {
        assertThatThrownBy(() -> feedService.getMonthlyHive("tester", 2026, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("미래 달");
    }

    @Test
    @DisplayName("올바르지 않은 월은 400 예외를 던진다")
    void 잘못된_연월_거부() {
        assertThatThrownBy(() -> feedService.getMonthlyHive("tester", 2026, 13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("올바르지 않은 연월");
    }

    @Test
    @DisplayName("보관함은 이번 달을 제외한 최근 N개월을 과거부터 순서대로 반환한다")
    void 보관함_월별_집계() {
        given(todoWorkItemRepository.countDailySuccessByAssignee(eq(1L), any(), any()))
                .willReturn(List.of(
                        dayCount(LocalDate.of(2026, 6, 1), 1),
                        dayCount(LocalDate.of(2026, 6, 2), 4),
                        dayCount(LocalDate.of(2026, 7, 10), 1)
                ));

        List<HiveArchiveMonthResponse> archive = feedService.getHiveArchive("tester", 3);

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
        assertThatThrownBy(() -> feedService.getHiveArchive("tester", 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> feedService.getHiveArchive("tester", 13))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 예외를 던진다")
    void 없는_사용자_거부() {
        given(userRepository.findByLoginId("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.getMonthlyHive("ghost", 2026, 8))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }
}
