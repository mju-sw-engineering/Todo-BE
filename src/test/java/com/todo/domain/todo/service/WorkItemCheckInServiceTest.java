package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.dto.request.CheckInRequest;
import com.todo.domain.todo.dto.response.CheckInResponse;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemCheckIn;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WorkItemCheckInServiceTest {

    private static final Long WORK_ITEM_ID = 10L;
    private static final Long TEAM_ID = 1L;

    @InjectMocks
    private WorkItemCheckInService workItemCheckInService;

    @Mock
    private WorkItemCheckInRepository workItemCheckInRepository;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void 진행_중인_본인_투두에_체크인한다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user);
        givenAccess(user, workItem);
        given(workItemCheckInRepository.existsByWorkItemIdAndUserIdAndCheckDate(any(), any(), any()))
                .willReturn(false);
        given(workItemCheckInRepository.saveAndFlush(any(WorkItemCheckIn.class)))
                .willAnswer(invocation -> {
                    WorkItemCheckIn saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 100L);
                    return saved;
                });

        CheckInResponse response = workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("오늘도 진행"));

        assertThat(response.checkInId()).isEqualTo(100L);
        assertThat(response.memo()).isEqualTo("오늘도 진행");
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void 다른_사람_투두에는_체크인할_수_없다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user(2L));
        givenAccess(user, workItem);

        assertThatThrownBy(() -> workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("메모")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        then(workItemCheckInRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void 완료된_투두에는_체크인할_수_없다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user);
        workItem.submit("proof-key");
        givenAccess(user, workItem);

        assertThatThrownBy(() -> workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("메모")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 같은_날_두_번째_체크인은_거부한다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user);
        givenAccess(user, workItem);
        given(workItemCheckInRepository.existsByWorkItemIdAndUserIdAndCheckDate(any(), any(), any()))
                .willReturn(true);

        assertThatThrownBy(() -> workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("메모")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        then(workItemCheckInRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청으로_unique_제약에_걸리면_충돌로_응답한다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user);
        givenAccess(user, workItem);
        given(workItemCheckInRepository.existsByWorkItemIdAndUserIdAndCheckDate(any(), any(), any()))
                .willReturn(false);
        given(workItemCheckInRepository.saveAndFlush(any(WorkItemCheckIn.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("메모")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 팀원이_아니면_체크인할_수_없다() {
        User user = user(1L);
        TodoWorkItem workItem = workItem(user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(WORK_ITEM_ID)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, user.getId())).willReturn(false);

        assertThatThrownBy(() -> workItemCheckInService.checkIn(
                "user1", WORK_ITEM_ID, new CheckInRequest("메모")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 팀원이면_체크인_목록을_조회할_수_있다() {
        User viewer = user(1L);
        User writer = user(2L);
        TodoWorkItem workItem = workItem(writer);
        givenAccess(viewer, workItem);
        WorkItemCheckIn checkIn = WorkItemCheckIn.create(workItem, writer, LocalDate.of(2026, 8, 7), "진행함");
        ReflectionTestUtils.setField(checkIn, "id", 100L);
        given(workItemCheckInRepository.findByWorkItemIdWithUser(WORK_ITEM_ID))
                .willReturn(List.of(checkIn));

        List<CheckInResponse> result = workItemCheckInService.getCheckIns("user1", WORK_ITEM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memo()).isEqualTo("진행함");
        assertThat(result.get(0).nickname()).isEqualTo("닉네임2");
    }

    private void givenAccess(User user, TodoWorkItem workItem) {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(WORK_ITEM_ID)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, user.getId())).willReturn(true);
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "닉네임" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private TodoWorkItem workItem(User assignee) {
        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        Todo todo = Todo.create(team, user(99L), "투두", null, LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(todo, "id", 5L);
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, assignee);
        ReflectionTestUtils.setField(workItem, "id", WORK_ITEM_ID);
        return workItem;
    }
}
