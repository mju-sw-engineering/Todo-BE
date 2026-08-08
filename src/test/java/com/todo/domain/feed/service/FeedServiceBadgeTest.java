package com.todo.domain.feed.service;

import com.todo.domain.feed.dto.response.BadgeResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * 배지 판정 테스트. 획득 여부는 저장하지 않고 최근 1년 활동(잔디·벌집과 같은 기준)으로
 * 조회 시점에 판정한다. 시간 고정이 필요해 Clock을 직접 주입한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedServiceBadgeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 테스트 기준 오늘: 2026-08-08 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);
    private static final Long TEAM_ID = 10L;

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

    private User me;

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

        me = user(1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        givenMyActivity(List.of());
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

    private static CheckInActivityRecord checkedIn(LocalDate day, long todoId) {
        return new CheckIn(day, 1L, todoId);
    }

    /** from(포함)부터 to(포함)까지 매일 서로 다른 투두에 체크인한 기록 */
    private static List<CheckInActivityRecord> dailyCheckIns(LocalDate from, LocalDate to, long userId) {
        List<CheckInActivityRecord> checkIns = new ArrayList<>();
        long todoId = 1_000 * userId;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            checkIns.add(new CheckIn(day, userId, todoId++));
        }
        return checkIns;
    }

    private void givenMyActivity(List<CheckInActivityRecord> checkIns) {
        lenient().when(todoRepository.findCreationActivityByCreatorId(eq(1L), any()))
                .thenReturn(List.of());
        lenient().when(todoWorkItemRepository.findSubmissionActivityByAssigneeId(eq(1L), any()))
                .thenReturn(List.of());
        lenient().when(workItemCheckInRepository.findActivityByUserId(eq(1L), any()))
                .thenReturn(checkIns);
    }

    private void givenMyTeam(List<User> members) {
        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        List<TeamMember> teamMembers = members.stream()
                .map(member -> TeamMember.create(team, member,
                        member == me ? TeamMemberRole.LEADER : TeamMemberRole.MEMBER))
                .toList();
        lenient().when(teamMemberRepository.findTeamsByUserId(1L)).thenReturn(List.of(team));
        lenient().when(teamMemberRepository.findByTeamIdWithUser(TEAM_ID)).thenReturn(teamMembers);
    }

    private void givenTeamActivity(List<CheckInActivityRecord> checkIns) {
        lenient().when(todoRepository.findCreationActivityByTeamId(eq(TEAM_ID), any()))
                .thenReturn(List.of());
        lenient().when(todoWorkItemRepository.findSubmissionActivityByTeamId(eq(TEAM_ID), any()))
                .thenReturn(List.of());
        lenient().when(workItemCheckInRepository.findActivityByTeamId(eq(TEAM_ID), any()))
                .thenReturn(checkIns);
    }

    private Map<String, Boolean> acquiredById(List<BadgeResponse> badges) {
        return badges.stream().collect(Collectors.toMap(BadgeResponse::id, BadgeResponse::acquired));
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "닉네임" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("활동이 없으면 6종 전부 미획득 상태로 카탈로그 순서대로 반환한다")
    void 활동_없음_전부_미획득() {
        List<BadgeResponse> badges = feedService.getBadges("1");

        assertThat(badges).extracting(BadgeResponse::id).containsExactly(
                "first-honey", "streak-7", "first-full-hive", "streak-30", "full-hive-3", "team-all-in");
        assertThat(badges).allMatch(badge -> !badge.acquired());
    }

    @Test
    @DisplayName("하루라도 활동하면 첫 꿀만 획득한다")
    void 첫_꿀_획득() {
        givenMyActivity(List.of(checkedIn(TODAY, 1L)));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("first-honey")).isTrue();
        assertThat(acquired.get("streak-7")).isFalse();
        assertThat(acquired.get("first-full-hive")).isFalse();
    }

    @Test
    @DisplayName("과거 7일 연속 기록은 스트릭이 끊긴 뒤에도 배지로 유지된다")
    void 스트릭7_과거_기록_유지() {
        // 7/1~7/7 연속 후 기록 없음 — 현재 스트릭은 0이지만 배지는 획득
        givenMyActivity(dailyCheckIns(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), 1L));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("streak-7")).isTrue();
        assertThat(acquired.get("streak-30")).isFalse();
    }

    @Test
    @DisplayName("30일 연속이면 7일·30일 배지를 모두 획득한다")
    void 스트릭30_획득() {
        givenMyActivity(dailyCheckIns(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 19), 1L));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("streak-7")).isTrue();
        assertThat(acquired.get("streak-30")).isTrue();
    }

    @Test
    @DisplayName("끊긴 구간은 이어 세지 않는다 — 6일 + 6일은 7일 배지가 아니다")
    void 스트릭_끊기면_리셋() {
        List<CheckInActivityRecord> checkIns = new ArrayList<>();
        checkIns.addAll(dailyCheckIns(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), 1L));
        checkIns.addAll(dailyCheckIns(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 13), 1L));
        givenMyActivity(checkIns);

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("streak-7")).isFalse();
    }

    @Test
    @DisplayName("지난달을 모든 날 채우면 첫 완주를 획득한다")
    void 첫_완주_획득() {
        givenMyActivity(dailyCheckIns(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 1L));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("first-full-hive")).isTrue();
        assertThat(acquired.get("full-hive-3")).isFalse();
        // 31일 연속이므로 스트릭 배지도 함께 획득된다
        assertThat(acquired.get("streak-30")).isTrue();
    }

    @Test
    @DisplayName("완주한 달이 3개면 3개월 완주를 획득한다")
    void 삼개월_완주_획득() {
        givenMyActivity(dailyCheckIns(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 31), 1L));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("first-full-hive")).isTrue();
        assertThat(acquired.get("full-hive-3")).isTrue();
    }

    @Test
    @DisplayName("진행 중인 이번 달은 완주로 세지 않는다")
    void 이번달은_완주_제외() {
        // 8/1~8/8(오늘)까지 모두 채워도 이번 달은 판정 대상이 아니다
        givenMyActivity(dailyCheckIns(LocalDate.of(2026, 8, 1), TODAY, 1L));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("first-full-hive")).isFalse();
    }

    @Test
    @DisplayName("2인 이상 팀에서 팀원 전원이 같은 날 기록을 남기면 팀 전원 참여를 획득한다")
    void 팀_전원_참여_획득() {
        givenMyTeam(List.of(me, user(2L)));
        LocalDate day = LocalDate.of(2026, 8, 5);
        givenTeamActivity(List.of(new CheckIn(day, 1L, 100L), new CheckIn(day, 2L, 101L)));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("team-all-in")).isTrue();
    }

    @Test
    @DisplayName("혼자인 팀은 팀 전원 참여 판정에서 제외한다")
    void 일인_팀은_제외() {
        givenMyTeam(List.of(me));
        givenTeamActivity(List.of(new CheckIn(LocalDate.of(2026, 8, 5), 1L, 100L)));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("team-all-in")).isFalse();
    }

    @Test
    @DisplayName("팀원이 아닌 사용자의 활동은 전원 참여 판정에 넣지 않는다")
    void 비팀원_활동은_무시() {
        givenMyTeam(List.of(me, user(2L)));
        LocalDate day = LocalDate.of(2026, 8, 5);
        // 나와 외부인(3L)만 활동 — 팀원 2L이 빠졌으므로 전원 참여가 아니다
        givenTeamActivity(List.of(new CheckIn(day, 1L, 100L), new CheckIn(day, 3L, 102L)));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("team-all-in")).isFalse();
    }

    @Test
    @DisplayName("팀원 전원이 서로 다른 날에만 기록하면 전원 참여가 아니다")
    void 다른_날_참여는_미획득() {
        givenMyTeam(List.of(me, user(2L)));
        givenTeamActivity(List.of(
                new CheckIn(LocalDate.of(2026, 8, 4), 1L, 100L),
                new CheckIn(LocalDate.of(2026, 8, 5), 2L, 101L)));

        Map<String, Boolean> acquired = acquiredById(feedService.getBadges("1"));

        assertThat(acquired.get("team-all-in")).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 인증 예외를 던진다")
    void 없는_사용자_거부() {
        assertThatThrownBy(() -> feedService.getBadges("999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
