package com.todo.domain.team.service;

import com.todo.domain.team.dto.response.TeamHiveResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * 팀 벌집 성장 테스트. 누적 기록 수는 (팀원, 날짜, 투두) 단위 고유 활동 수이고
 * 레벨 문턱값은 0 / 30 / 100 / 300이다.
 */
@ExtendWith(MockitoExtension.class)
class TeamHiveServiceTest {

    private static final Long TEAM_ID = 1L;

    @InjectMocks
    private TeamHiveService teamHiveService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Mock
    private WorkItemCheckInRepository workItemCheckInRepository;

    @BeforeEach
    void setUp() {
        User user = User.create("1", "encoded-password", "테스터", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        ReflectionTestUtils.setField(team, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        lenient().when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        lenient().when(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).thenReturn(true);

        givenTeamActivity(List.of(), List.of(), List.of());
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

    private void givenTeamActivity(
            List<UserActivityRecord> creations,
            List<UserActivityRecord> submissions,
            List<CheckInActivityRecord> checkIns
    ) {
        lenient().when(todoRepository.findCreationActivityByTeamId(eq(TEAM_ID), any())).thenReturn(creations);
        lenient().when(todoWorkItemRepository.findSubmissionActivityByTeamId(eq(TEAM_ID), any())).thenReturn(submissions);
        lenient().when(workItemCheckInRepository.findActivityByTeamId(eq(TEAM_ID), any())).thenReturn(checkIns);
    }

    /** 서로 다른 (사람, 날짜, 투두) 조합의 체크인 기록 count개 */
    private List<CheckInActivityRecord> records(int count) {
        List<CheckInActivityRecord> checkIns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            checkIns.add(new CheckIn(LocalDate.of(2026, 1, 1).plusDays(i % 200), 1L, 1000L + i));
        }
        return checkIns;
    }

    @Test
    @DisplayName("기록이 없으면 Lv.1이고 다음 문턱값은 30이다")
    void 기록_없음_레벨1() {
        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");

        assertThat(response.level()).isEqualTo(1);
        assertThat(response.totalRecords()).isZero();
        assertThat(response.currentThreshold()).isZero();
        assertThat(response.nextThreshold()).isEqualTo(30);
    }

    @Test
    @DisplayName("같은 사람이 같은 날 같은 투두를 생성·제출·체크인해도 기록 1개로 센다")
    void 중복_활동_한_개() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        givenTeamActivity(
                List.of(new Submission(day.atTime(9, 0), 1L, 100L)),
                List.of(new Submission(day.atTime(18, 0), 1L, 100L)),
                List.of(new CheckIn(day, 1L, 100L))
        );

        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");

        assertThat(response.totalRecords()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 팀원이 같은 날 같은 투두에 활동하면 각각 센다")
    void 팀원별_기록_구분() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        givenTeamActivity(
                List.of(),
                List.of(new Submission(day.atTime(9, 0), 1L, 100L), new Submission(day.atTime(10, 0), 2L, 100L)),
                List.of(new CheckIn(day.plusDays(1), 1L, 100L))
        );

        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");

        // 다른 사람 + 같은 투두, 같은 사람 + 다른 날 모두 별도 기록
        assertThat(response.totalRecords()).isEqualTo(3);
    }

    @Test
    @DisplayName("문턱값 경계에서 레벨이 올라간다 — 29개는 Lv.1, 30개는 Lv.2")
    void 레벨2_경계() {
        givenTeamActivity(List.of(), List.of(), records(29));
        assertThat(teamHiveService.getTeamHive(TEAM_ID, "1").level()).isEqualTo(1);

        givenTeamActivity(List.of(), List.of(), records(30));
        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");
        assertThat(response.level()).isEqualTo(2);
        assertThat(response.currentThreshold()).isEqualTo(30);
        assertThat(response.nextThreshold()).isEqualTo(100);
    }

    @Test
    @DisplayName("100개는 Lv.3이다")
    void 레벨3_경계() {
        givenTeamActivity(List.of(), List.of(), records(100));

        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");

        assertThat(response.level()).isEqualTo(3);
        assertThat(response.nextThreshold()).isEqualTo(300);
    }

    @Test
    @DisplayName("300개 이상이면 최고 레벨이고 다음 문턱값이 없다")
    void 최고_레벨() {
        givenTeamActivity(List.of(), List.of(), records(300));

        TeamHiveResponse response = teamHiveService.getTeamHive(TEAM_ID, "1");

        assertThat(response.level()).isEqualTo(4);
        assertThat(response.currentThreshold()).isEqualTo(300);
        assertThat(response.nextThreshold()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 팀은 404 예외를 던진다")
    void 없는_팀_거부() {
        lenient().when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamHiveService.getTeamHive(99L, "1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("팀원이 아니면 403 예외를 던진다")
    void 비팀원_거부() {
        lenient().when(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).thenReturn(false);

        assertThatThrownBy(() -> teamHiveService.getTeamHive(TEAM_ID, "1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
