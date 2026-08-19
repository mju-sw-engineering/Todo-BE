package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.AssignTodoWorkItemRequest;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.CreateTodoTaskRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoActivePageResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemSubmissionResponse;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.TodoWorkItemSummary;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    private static final Long TEAM_ID = 100L;
    private static final Long TODO_ID = 10L;

    @InjectMocks
    private TodoService todoService;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @BeforeEach
    void setUp() {
        // 상태 전이는 목이 아니라 실제 구현을 주입한다.
        // 제출 경로가 공통 서비스와 합쳐진 뒤에도 같은 상태·카운터 결과를 내는지가 이 테스트의 검증 대상이다.
        ReflectionTestUtils.setField(
                todoService,
                "todoStatusTransitionService",
                new TodoStatusTransitionService(
                        todoWorkItemRepository, teamRepository, teamMemberRepository,
                        notificationService, notificationMessageFactory)
        );
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void DIRECT_생성은_담당자마다_DIRECT_WorkItem을_만든다() {
        User creator = user(1L);
        User firstAssignee = user(2L);
        User secondAssignee = user(3L);
        Team team = team();
        givenCreateAccess(creator, team);
        given(todoRepository.save(any(Todo.class))).willAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            setId(todo, TODO_ID);
            return todo;
        });
        given(userRepository.findById(2L)).willReturn(Optional.of(firstAssignee));
        given(userRepository.findById(3L)).willReturn(Optional.of(secondAssignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 2L)).willReturn(true);
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 3L)).willReturn(true);

        CreateTodoResponse response = todoService.createTodo("1", TEAM_ID, new CreateTodoRequest(
                "공통 인증", "설명", futureOffset(3), List.of(2L, 3L), null
        ));

        assertThat(response.mode()).isEqualTo(TodoMode.DIRECT);
        assertThat(response.directAssignees()).hasSize(2);
        assertThat(response.tasks()).isEmpty();
        ArgumentCaptor<List<TodoWorkItem>> workItems = ArgumentCaptor.forClass(List.class);
        then(todoWorkItemRepository).should().saveAll(workItems.capture());
        assertThat(workItems.getValue())
                .extracting(TodoWorkItem::getType)
                .containsOnly(WorkItemType.DIRECT);
        assertThat(workItems.getValue())
                .extracting(workItem -> workItem.getAssignee().getId())
                .containsExactly(2L, 3L);
    }

    @Test
    void TASK_생성은_같은_담당자에게도_여러_Task_WorkItem과_순서를_보존한다() {
        User creator = user(1L);
        User assignee = user(2L);
        Team team = team();
        givenCreateAccess(creator, team);
        given(todoRepository.save(any(Todo.class))).willAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            setId(todo, TODO_ID);
            return todo;
        });
        given(userRepository.findById(2L)).willReturn(Optional.of(assignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 2L)).willReturn(true);

        CreateTodoResponse response = todoService.createTodo("1", TEAM_ID, new CreateTodoRequest(
                "발표 준비",
                null,
                futureOffset(3),
                null,
                List.of(
                        new CreateTodoTaskRequest("자료 조사", null, 2L, futureOffset(1)),
                        new CreateTodoTaskRequest("발표 자료", "슬라이드", 2L, futureOffset(2))
                )
        ));

        assertThat(response.mode()).isEqualTo(TodoMode.TASK);
        assertThat(response.directAssignees()).isEmpty();
        assertThat(response.tasks()).extracting(task -> task.title()).containsExactly("자료 조사", "발표 자료");
        assertThat(response.tasks()).extracting(task -> task.position()).containsExactly(0, 1);
    }

    @Test
    void TASK_생성시_개별_마감을_비우면_투두_전체_마감시간이_응답에_그대로_반영된다() {
        User creator = user(1L);
        User assignee = user(2L);
        Team team = team();
        givenCreateAccess(creator, team);
        given(todoRepository.save(any(Todo.class))).willAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            setId(todo, TODO_ID);
            return todo;
        });
        given(userRepository.findById(2L)).willReturn(Optional.of(assignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 2L)).willReturn(true);
        OffsetDateTime todoDeadline = futureOffset(3);

        CreateTodoResponse response = todoService.createTodo("1", TEAM_ID, new CreateTodoRequest(
                "발표 준비",
                null,
                todoDeadline,
                null,
                List.of(new CreateTodoTaskRequest("자료 조사", null, 2L, null))
        ));

        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().get(0).deadline()).isEqualTo(response.deadline());
    }

    @Test
    void 생성은_DIRECT_담당자와_TASK를_함께_요청하면_거절한다() {
        User creator = user(1L);
        givenCreateAccess(creator, team());

        assertThatThrownBy(() -> todoService.createTodo("1", TEAM_ID, new CreateTodoRequest(
                "잘못된 요청",
                null,
                futureOffset(1),
                List.of(2L),
                List.of(new CreateTodoTaskRequest("작업", null, 2L, futureOffset(1)))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("assigneeIds와 tasks 중 하나만 입력해야 합니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void TASK_마감이_현재보다_과거면_생성을_거절한다() {
        User creator = user(1L);
        User assignee = user(2L);
        Team team = team();
        givenCreateAccess(creator, team);
        given(userRepository.findById(2L)).willReturn(Optional.of(assignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 2L)).willReturn(true);

        assertThatThrownBy(() -> todoService.createTodo("1", TEAM_ID, new CreateTodoRequest(
                "마감된 작업",
                null,
                futureOffset(1),
                null,
                List.of(new CreateTodoTaskRequest(
                        "이미 마감된 Task",
                        null,
                        2L,
                        OffsetDateTime.now(ZoneOffset.ofHours(9)).minusMinutes(1)
                ))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task 마감은 현재보다 미래여야 합니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(todoWorkItemRepository).shouldHaveNoInteractions();
    }

    @Test
    void 마감된_TASK가_부모를_FAIL로_만든_뒤에도_다른_진행중_TASK는_제출할_수_있다() {
        User firstAssignee = user(1L);
        User secondAssignee = user(2L);
        Team team = team();
        Todo todo = todo(team, TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(3));
        TodoWorkItem expired = task(todo, firstAssignee, 20L, "지난 작업", LocalDateTime.now().minusMinutes(1), 0);
        TodoWorkItem remaining = task(todo, secondAssignee, 21L, "남은 작업", LocalDateTime.now().plusDays(1), 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(firstAssignee));
        given(userRepository.findById(2L)).willReturn(Optional.of(secondAssignee));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(expired));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(21L)).willReturn(Optional.of(remaining));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(20L)).willReturn(Optional.of(expired));
        given(todoWorkItemRepository.findByIdWithLock(21L)).willReturn(Optional.of(remaining));
        given(fileService.createProofThumbnail("remaining-proof")).willReturn("remaining-thumb");
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(2L);
        // 실패한 TASK가 하나 남아 있으므로 재평가는 FAIL 단계에서 끝나고 성공 개수는 보지 않는다.
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(1L);

        assertThatThrownBy(() -> todoService.submitTodoWorkItem(20L, "1", new SubmitTodoRequest("expired-proof")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("마감 시간이 지났습니다.");
        assertThat(expired.getStatus()).isEqualTo(WorkItemStatus.FAIL);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);

        todoService.submitTodoWorkItem(21L, "2", new SubmitTodoRequest("remaining-proof"));

        assertThat(remaining.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
        then(teamRepository).shouldHaveNoInteractions();
    }

    @Test
    void WorkItem_제출은_담당자가_아니면_403을_반환하고_파일을_검증하지_않는다() {
        User assignee = user(1L);
        User outsider = user(2L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, assignee, 20L, "담당 작업", LocalDateTime.now().plusDays(1), 0);
        given(userRepository.findById(2L)).willReturn(Optional.of(outsider));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(20L)).willReturn(Optional.of(workItem));

        assertThatThrownBy(() -> todoService.submitTodoWorkItem(20L, "2", new SubmitTodoRequest("proof")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 WorkItem의 담당자가 아닙니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        then(fileService).shouldHaveNoInteractions();
    }

    @Test
    void 다른_WorkItem에_사용된_인증사진은_중복_제출할_수_없다() {
        User assignee = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, assignee, 20L, "새 작업", LocalDateTime.now().plusDays(1), 0);
        given(userRepository.findById(1L)).willReturn(Optional.of(assignee));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(20L)).willReturn(Optional.of(workItem));
        given(todoWorkItemRepository.existsByProofImageKey("used-proof")).willReturn(true);

        assertThatThrownBy(() -> todoService.submitTodoWorkItem(20L, "1", new SubmitTodoRequest("used-proof")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 다른 WorkItem에 제출된 인증 사진입니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(fileService).should().validateProofImage(1L, "used-proof");
        then(fileService).shouldHaveNoMoreInteractions();
    }

    @Test
    void 마지막_WorkItem_제출은_부모를_성공으로_한번만_전이시키고_중복_제출은_409다() {
        User assignee = user(1L);
        Team team = team();
        Todo todo = todo(team, TodoMode.DIRECT, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = direct(todo, assignee, 20L);
        given(userRepository.findById(1L)).willReturn(Optional.of(assignee));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(20L)).willReturn(Optional.of(workItem));
        given(fileService.createProofThumbnail("proof")).willReturn("thumb");
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(1L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(1L);

        todoService.submitTodoWorkItem(20L, "1", new SubmitTodoRequest("proof"));

        assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        then(teamRepository).should().incrementSuccessCount(TEAM_ID);

        assertThatThrownBy(() -> todoService.submitTodoWorkItem(20L, "1", new SubmitTodoRequest("another-proof")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출했거나 종료된 WorkItem입니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        then(teamRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void 미배정_TASK는_팀원이_재배정할_수_있다() {
        User requester = user(1L);
        User newAssignee = user(2L);
        Team team = team();
        Todo todo = todo(team, TodoMode.TASK, TodoStatus.FAIL, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, null, 20L, "인수인계", LocalDateTime.now().plusDays(1), 0);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(userRepository.findById(2L)).willReturn(Optional.of(newAssignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 2L)).willReturn(true);
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(20L)).willReturn(Optional.of(workItem));

        TodoWorkItemAssigneeResponse response = todoService.reassignTodoWorkItem(
                20L, "1", new AssignTodoWorkItemRequest(2L)
        );

        assertThat(response.assigneeId()).isEqualTo(2L);
        assertThat(workItem.getAssignee()).isSameAs(newAssignee);
    }

    @Test
    void 팀원은_제출사진의_원본과_썸네일_URL을_조회할_수_있다() {
        User viewer = user(1L);
        User assignee = user(2L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, assignee, 20L, "사진 작업", LocalDateTime.now().plusDays(1), 0);
        setField(workItem, "proofImageKey", "proof/original.jpg");
        setField(workItem, "proofThumbnailKey", "proof/thumb.webp");
        setField(workItem, "submittedAt", LocalDateTime.of(2026, 8, 2, 12, 0));
        given(userRepository.findById(1L)).willReturn(Optional.of(viewer));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(fileService.resolveImageUrl("proof/original.jpg")).willReturn("original-url");
        given(fileService.resolveImageUrl("proof/thumb.webp")).willReturn("thumb-url");
        given(fileService.getPresignedUrlExpiration()).willReturn(Duration.ofMinutes(10));

        TodoWorkItemSubmissionResponse response = todoService.getTodoWorkItemSubmission(20L, "1");

        assertThat(response.workItemId()).isEqualTo(20L);
        assertThat(response.assigneeId()).isEqualTo(2L);
        assertThat(response.originalUrl()).isEqualTo("original-url");
        assertThat(response.thumbnailUrl()).isEqualTo("thumb-url");
        assertThat(response.submittedAt()).isEqualTo(OffsetDateTime.of(2026, 8, 2, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void 제출된_WorkItem에는_팀원이_반응을_새로_남길_수_있다() {
        User reactor = user(1L);
        User assignee = user(2L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, assignee, 20L, "반응 대상", LocalDateTime.now().plusDays(1), 0);
        setField(workItem, "proofImageKey", "proof.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(reactor));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoWorkItemIdAndUserId(20L, 1L)).willReturn(Optional.empty());
        given(todoReactionRepository.save(any(TodoReaction.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(todoReactionRepository.countByTodoWorkItemIdAndReactionType(20L, TodoReactionType.LIKE)).willReturn(1L);

        TodoReactionResponse response = todoService.reactTodoWorkItem(20L, "1", new ReactTodoRequest(TodoReactionType.LIKE));

        assertThat(response.type()).isEqualTo(TodoReactionType.LIKE);
        assertThat(response.count()).isEqualTo(1L);
        then(todoReactionRepository).should().save(any(TodoReaction.class));
        then(notificationService).should().send(eq(assignee), eq(reactor), any(), eq(todo.getId()), eq(TEAM_ID));
    }

    @Test
    void 본인_제출물에_본인이_반응해도_알림을_보내지_않는다() {
        User self = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, self, 20L, "반응 대상", LocalDateTime.now().plusDays(1), 0);
        setField(workItem, "proofImageKey", "proof.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(self));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoWorkItemIdAndUserId(20L, 1L)).willReturn(Optional.empty());
        given(todoReactionRepository.save(any(TodoReaction.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(todoReactionRepository.countByTodoWorkItemIdAndReactionType(20L, TodoReactionType.LIKE)).willReturn(1L);

        todoService.reactTodoWorkItem(20L, "1", new ReactTodoRequest(TodoReactionType.LIKE));

        then(notificationService).should(never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void 목록은_WorkItem_기준_달성수와_내_여러_작업상태를_요약한다() {
        User viewer = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        givenListAccess(viewer);
        given(todoRepository.findByTeamIdWithCreator(TEAM_ID)).willReturn(List.of(todo));
        given(todoWorkItemRepository.findSummaryByTodoIdIn(List.of(TODO_ID))).willReturn(List.of(
                summary(TODO_ID, 1L, WorkItemStatus.SUCCESS, WorkItemType.TASK, 0),
                summary(TODO_ID, 1L, WorkItemStatus.FAIL, WorkItemType.TASK, 1),
                summary(TODO_ID, 1L, WorkItemStatus.IN_PROGRESS, WorkItemType.TASK, 2),
                summary(TODO_ID, 2L, WorkItemStatus.SUCCESS, WorkItemType.TASK, 3)
        ));

        TodoSummaryResponse response = todoService.getTodoList(TEAM_ID, "1", null, null).get(0);

        assertThat(response.achievementCount()).isEqualTo("2 / 4");
        assertThat(response.myWorkSummary().totalCount()).isEqualTo(3);
        assertThat(response.myWorkSummary().successCount()).isEqualTo(1);
        assertThat(response.myWorkSummary().failCount()).isEqualTo(1);
        assertThat(response.myWorkSummary().inProgressCount()).isEqualTo(1);
    }

    @Test
    void DIRECT_상세는_DIRECT_담당자만_노출하고_TASK_배열은_비운다() {
        User viewer = user(1L);
        Todo todo = todo(team(), TodoMode.DIRECT, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = direct(todo, viewer, 20L);
        givenDetailAccess(viewer, todo);
        given(todoWorkItemRepository.findByTodoIdOrderByPositionAsc(TODO_ID)).willReturn(List.of(workItem));
        given(todoReactionRepository.countByTodoWorkItemIds(List.of(20L))).willReturn(List.of());
        given(todoReactionRepository.findByTodoWorkItemIdInAndUserId(List.of(20L), 1L)).willReturn(List.of());

        TodoDetailResponse response = todoService.getTodoDetail(TODO_ID, "1");

        assertThat(response.mode()).isEqualTo(TodoMode.DIRECT);
        assertThat(response.directAssignees()).hasSize(1);
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    void TASK_상세는_TASK만_노출하고_DIRECT_배열은_비운다() {
        User viewer = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem workItem = task(todo, viewer, 20L, "세부 작업", LocalDateTime.now().plusDays(1), 0);
        givenDetailAccess(viewer, todo);
        given(todoWorkItemRepository.findByTodoIdOrderByPositionAsc(TODO_ID)).willReturn(List.of(workItem));
        given(todoReactionRepository.countByTodoWorkItemIds(List.of(20L))).willReturn(List.of());
        given(todoReactionRepository.findByTodoWorkItemIdInAndUserId(List.of(20L), 1L)).willReturn(List.of());

        TodoDetailResponse response = todoService.getTodoDetail(TODO_ID, "1");

        assertThat(response.mode()).isEqualTo(TodoMode.TASK);
        assertThat(response.directAssignees()).isEmpty();
        assertThat(response.tasks()).extracting(task -> task.title()).containsExactly("세부 작업");
    }

    @Test
    void 목록은_전체_상태_날짜_필터를_지원하고_잘못된_조합은_거절한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        assertThat(todoService.getTodoList(TEAM_ID, "1", null, null)).isEmpty();
        assertThat(todoService.getTodoList(TEAM_ID, "1", "", null)).isEmpty();
        assertThat(todoService.getTodoList(TEAM_ID, "1", "IN_PROGRESS", null)).isEmpty();
        assertThat(todoService.getTodoList(TEAM_ID, "1", "ENDED", null)).isEmpty();
        assertThat(todoService.getTodoList(TEAM_ID, "1", null, "2026-08-02")).isEmpty();

        assertThatThrownBy(() -> todoService.getTodoList(TEAM_ID, "1", "UNKNOWN", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("알 수 없는 투두 필터입니다.");
        assertThatThrownBy(() -> todoService.getTodoList(TEAM_ID, "1", null, "2026/08/02"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("date 형식은 yyyy-MM-dd 이어야 합니다.");
        assertThatThrownBy(() -> todoService.getTodoList(TEAM_ID, "1", "ENDED", "2026-08-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("filter와 date는 함께 사용할 수 없습니다.");
    }

    @Test
    void 마감_미경과_목록은_cursor가_없으면_status_없을_때_전체_상태로_첫_페이지를_조회한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        todoService.getActiveTodoList(TEAM_ID, "1", null, null, 20);

        then(todoRepository).should().findFirstActivePageByTeamId(
                eq(TEAM_ID),
                eq(List.of(TodoStatus.IN_PROGRESS, TodoStatus.SUCCESS, TodoStatus.FAIL)),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 21))
        );
    }

    @Test
    void 마감_미경과_목록은_status_PENDING이면_진행중만_대상으로_조회한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        todoService.getActiveTodoList(TEAM_ID, "1", "PENDING", null, 20);

        then(todoRepository).should().findFirstActivePageByTeamId(
                eq(TEAM_ID),
                eq(List.of(TodoStatus.IN_PROGRESS)),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 21))
        );
    }

    @Test
    void 마감_미경과_목록은_status_DONE이면_성공과_실패를_대상으로_조회한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        todoService.getActiveTodoList(TEAM_ID, "1", "DONE", null, 20);

        then(todoRepository).should().findFirstActivePageByTeamId(
                eq(TEAM_ID),
                eq(List.of(TodoStatus.SUCCESS, TodoStatus.FAIL)),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 21))
        );
    }

    @Test
    void 마감_미경과_목록은_알_수_없는_status면_거절한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        assertThatThrownBy(() -> todoService.getActiveTodoList(TEAM_ID, "1", "UNKNOWN", null, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("알 수 없는 status 값입니다.");
    }

    @Test
    void 마감_미경과_목록은_cursor가_있으면_deadline과_id로_분해해서_다음_페이지를_조회한다() {
        User viewer = user(1L);
        givenListAccess(viewer);
        LocalDateTime cursorDeadline = LocalDateTime.of(2026, 8, 15, 10, 0, 0);
        Long cursorId = 22L;

        todoService.getActiveTodoList(TEAM_ID, "1", null, encodeCursor(cursorDeadline, cursorId), 20);

        then(todoRepository).should().findNextActivePageByTeamId(
                eq(TEAM_ID),
                eq(List.of(TodoStatus.IN_PROGRESS, TodoStatus.SUCCESS, TodoStatus.FAIL)),
                any(LocalDateTime.class),
                eq(cursorDeadline),
                eq(cursorId),
                eq(PageRequest.of(0, 21))
        );
    }

    @Test
    void 마감_미경과_목록은_잘못된_형식의_cursor면_거절한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        assertThatThrownBy(() -> todoService.getActiveTodoList(TEAM_ID, "1", null, "잘못된-커서", 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 마감_미경과_목록은_size보다_많이_조회되면_hasNext가_true이고_마지막으로_반환된_항목_기준으로_커서를_인코딩한다() {
        User viewer = user(1L);
        givenListAccess(viewer);
        Team team = team();
        Todo first = todo(team, TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        Todo second = todo(team, TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        setId(second, 11L);
        given(todoRepository.findFirstActivePageByTeamId(eq(TEAM_ID), any(), any(LocalDateTime.class), eq(PageRequest.of(0, 2))))
                .willReturn(List.of(first, second));
        given(todoWorkItemRepository.findSummaryByTodoIdIn(any())).willReturn(List.of());

        TodoActivePageResponse response = todoService.getActiveTodoList(TEAM_ID, "1", null, null, 1);

        assertThat(response.todos()).hasSize(1);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(encodeCursor(first.getDeadline(), first.getId()));
    }

    @Test
    void 마감_미경과_목록은_size_이하로_조회되면_hasNext가_false이고_nextCursor가_없다() {
        User viewer = user(1L);
        givenListAccess(viewer);
        Todo only = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        given(todoRepository.findFirstActivePageByTeamId(eq(TEAM_ID), any(), any(LocalDateTime.class), eq(PageRequest.of(0, 21))))
                .willReturn(List.of(only));
        given(todoWorkItemRepository.findSummaryByTodoIdIn(any())).willReturn(List.of());

        TodoActivePageResponse response = todoService.getActiveTodoList(TEAM_ID, "1", null, null, 20);

        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 마감_미경과_목록도_내_작업_요약을_포함한_기존_변환_로직을_그대로_탄다() {
        User viewer = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        givenListAccess(viewer);
        given(todoRepository.findFirstActivePageByTeamId(eq(TEAM_ID), any(), any(LocalDateTime.class), any()))
                .willReturn(List.of(todo));
        given(todoWorkItemRepository.findSummaryByTodoIdIn(List.of(TODO_ID))).willReturn(List.of(
                summary(TODO_ID, 1L, WorkItemStatus.IN_PROGRESS, WorkItemType.TASK, 0)
        ));

        TodoSummaryResponse response = todoService.getActiveTodoList(TEAM_ID, "1", null, null, 20).todos().get(0);

        assertThat(response.myWorkSummary().totalCount()).isEqualTo(1);
        assertThat(response.myWorkSummary().inProgressCount()).isEqualTo(1);
    }

    private String encodeCursor(LocalDateTime deadline, Long id) {
        String raw = deadline + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 기간_리포트는_입력날짜를_검증하고_WorkItem_마감일_기준으로_집계한다() {
        User viewer = user(1L);
        givenListAccess(viewer);

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(TEAM_ID, "1", null, "2026-08-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate 파라미터는 필수입니다.");
        assertThatThrownBy(() -> todoService.getTodoPeriodReport(TEAM_ID, "1", " ", "2026-08-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate 파라미터는 필수입니다.");
        assertThatThrownBy(() -> todoService.getTodoPeriodReport(TEAM_ID, "1", "invalid", "2026-08-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate 형식은 yyyy-MM-dd 이어야 합니다.");
        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                TEAM_ID, "1", "2026-08-03", "2026-08-02"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate는 endDate보다 늦을 수 없습니다.");

        LocalDate reportDate = LocalDate.of(2026, 8, 2);
        Team team = team();
        Todo inProgressTodo = todo(
                team,
                TodoMode.TASK,
                TodoStatus.IN_PROGRESS,
                reportDate.atTime(23, 0)
        );
        TodoWorkItem success = task(inProgressTodo, user(1L), 20L, "완료", reportDate.atTime(10, 0), 0);
        success.markAsSuccess();
        TodoWorkItem inProgress = task(inProgressTodo, user(2L), 21L, "진행", reportDate.atTime(12, 0), 1);
        Todo failedTodo = todo(team, TodoMode.TASK, TodoStatus.FAIL, reportDate.atTime(22, 0));
        setId(failedTodo, 11L);
        TodoWorkItem fail = task(failedTodo, user(3L), 22L, "실패", reportDate.atTime(14, 0), 0);
        fail.markAsFail();
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(
                TEAM_ID,
                reportDate.atStartOfDay(),
                reportDate.plusDays(2).atStartOfDay()
        )).willReturn(List.of(success, inProgress, fail));

        TodoPeriodReportResponse response = todoService.getTodoPeriodReport(
                TEAM_ID,
                "1",
                reportDate.toString(),
                reportDate.plusDays(1).toString()
        );

        assertThat(response.summary().totalTodoCount()).isEqualTo(3);
        assertThat(response.summary().successCount()).isEqualTo(1);
        assertThat(response.summary().failCount()).isEqualTo(1);
        assertThat(response.summary().inProgressCount()).isEqualTo(1);
        assertThat(response.dailyStats()).hasSize(2);
        assertThat(response.weakestDay().date()).isEqualTo(reportDate);
        assertThat(response.actionCandidates()).hasSize(1);
        assertThat(response.actionCandidates().get(0).todoId()).isEqualTo(TODO_ID);
    }

    @Test
    void 기간_리포트는_최대_기간까지는_조회한다() {
        User viewer = user(1L);
        givenListAccess(viewer);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(365); // 양끝 포함 366일
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(
                TEAM_ID,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        )).willReturn(List.of());

        TodoPeriodReportResponse response = todoService.getTodoPeriodReport(
                TEAM_ID, "1", start.toString(), end.toString());

        assertThat(response.dailyStats()).hasSize(366);
    }

    @Test
    void 기간_리포트는_최대_기간을_넘으면_조회하지_않고_거부한다() {
        User viewer = user(1L);
        givenListAccess(viewer);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(366); // 양끝 포함 367일

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                TEAM_ID, "1", start.toString(), end.toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 기간은 최대 366일까지 가능합니다.");

        // 상한 검사는 비싼 조회 앞에 있어야 한다
        then(todoWorkItemRepository).should(never())
                .findByTeamIdAndEffectiveDeadlineBetween(any(), any(), any());
    }

    @Test
    void 제출사진_조회는_썸네일이_없으면_원본을_쓰고_미제출은_404다() {
        User viewer = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(2));
        TodoWorkItem submitted = task(todo, user(2L), 20L, "제출", LocalDateTime.now().plusDays(1), 0);
        setField(submitted, "proofImageKey", "proof/original.jpg");
        setField(submitted, "submittedAt", LocalDateTime.now());
        TodoWorkItem unsubmitted = task(todo, user(2L), 21L, "미제출", LocalDateTime.now().plusDays(1), 1);
        TodoWorkItem missingTime = task(todo, user(2L), 22L, "시각 누락", LocalDateTime.now().plusDays(1), 2);
        setField(missingTime, "proofImageKey", "proof/missing-time.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(viewer));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(submitted));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(21L)).willReturn(Optional.of(unsubmitted));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(22L)).willReturn(Optional.of(missingTime));
        given(fileService.resolveImageUrl("proof/original.jpg")).willReturn("original-url");
        given(fileService.getPresignedUrlExpiration()).willReturn(Duration.ofMinutes(10));

        TodoWorkItemSubmissionResponse response = todoService.getTodoWorkItemSubmission(20L, "1");

        assertThat(response.thumbnailUrl()).isEqualTo("original-url");
        assertThatThrownBy(() -> todoService.getTodoWorkItemSubmission(21L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제출된 인증 사진이 없습니다.");
        assertThatThrownBy(() -> todoService.getTodoWorkItemSubmission(22L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제출된 인증 사진이 없습니다.");
    }

    @Test
    void 같은_반응은_취소하고_다른_반응은_변경한다() {
        User reactor = user(1L);
        Todo todo = todo(team(), TodoMode.DIRECT, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoWorkItem workItem = direct(todo, user(2L), 20L);
        setField(workItem, "proofImageKey", "proof.jpg");
        TodoReaction same = TodoReaction.create(workItem, reactor, TodoReactionType.LIKE);
        TodoReaction different = TodoReaction.create(workItem, reactor, TodoReactionType.HEART);
        given(userRepository.findById(1L)).willReturn(Optional.of(reactor));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(workItem));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoWorkItemIdAndUserId(20L, 1L))
                .willReturn(Optional.of(same), Optional.of(different));

        TodoReactionResponse cancelled = todoService.reactTodoWorkItem(
                20L,
                "1",
                new ReactTodoRequest(TodoReactionType.LIKE)
        );
        TodoReactionResponse changed = todoService.reactTodoWorkItem(
                20L,
                "1",
                new ReactTodoRequest(TodoReactionType.LIKE)
        );

        assertThat(cancelled.count()).isZero();
        assertThat(changed.type()).isEqualTo(TodoReactionType.LIKE);
        assertThat(different.getReactionType()).isEqualTo(TodoReactionType.LIKE);
        then(todoReactionRepository).should().delete(same);
    }

    @Test
    void 재배정은_이미_배정된_항목을_400으로_거절하고_자기배정에는_알림을_보내지_않는다() {
        User requester = user(1L);
        Todo todo = todo(team(), TodoMode.TASK, TodoStatus.FAIL, LocalDateTime.now().plusDays(2));
        TodoWorkItem assigned = task(todo, user(2L), 20L, "배정됨", LocalDateTime.now().plusDays(1), 0);
        TodoWorkItem unassigned = task(todo, null, 21L, "미배정", LocalDateTime.now().plusDays(1), 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(true);
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(assigned));
        given(todoWorkItemRepository.findByIdWithTodoAndTeam(21L)).willReturn(Optional.of(unassigned));
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(21L)).willReturn(Optional.of(unassigned));

        assertThatThrownBy(() -> todoService.reassignTodoWorkItem(
                20L, "1", new AssignTodoWorkItemRequest(1L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 담당자가 배정된 WorkItem입니다.");

        TodoWorkItemAssigneeResponse response = todoService.reassignTodoWorkItem(
                21L, "1", new AssignTodoWorkItemRequest(1L)
        );

        assertThat(response.assigneeId()).isEqualTo(1L);
        then(notificationService).shouldHaveNoInteractions();
    }

    private void givenCreateAccess(User creator, Team team) {
        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, creator.getId())).willReturn(true);
    }

    private void givenListAccess(User user) {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsById(TEAM_ID)).willReturn(true);
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, user.getId())).willReturn(true);
    }

    private void givenDetailAccess(User viewer, Todo todo) {
        given(userRepository.findById(1L)).willReturn(Optional.of(viewer));
        given(todoRepository.findByIdWithCreatorAndTeam(TODO_ID)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, viewer.getId())).willReturn(true);
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "닉네임" + id, null);
        setId(user, id);
        return user;
    }

    private Team team() {
        Team team = Team.create("팀", null, "invite-code");
        setId(team, TEAM_ID);
        return team;
    }

    private Todo todo(Team team, TodoMode mode, TodoStatus status, LocalDateTime deadline) {
        Todo todo = Todo.create(team, user(99L), "투두", "설명", deadline, mode);
        setId(todo, TODO_ID);
        setField(todo, "status", status);
        return todo;
    }

    private TodoWorkItem direct(Todo todo, User assignee, Long id) {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, assignee);
        setId(workItem, id);
        return workItem;
    }

    private TodoWorkItem task(Todo todo, User assignee, Long id, String title, LocalDateTime deadline, int position) {
        TodoWorkItem workItem = TodoWorkItem.createTask(todo, assignee, title, null, deadline, position);
        setId(workItem, id);
        return workItem;
    }

    private TodoWorkItemSummary summary(
            Long todoId,
            Long assigneeId,
            WorkItemStatus status,
            WorkItemType type,
            int position
    ) {
        return new TodoWorkItemSummary() {
            @Override
            public Long getTodoId() {
                return todoId;
            }

            @Override
            public Long getAssigneeId() {
                return assigneeId;
            }

            @Override
            public String getNickname() {
                return "닉네임" + assigneeId;
            }

            @Override
            public WorkItemStatus getStatus() {
                return status;
            }

            @Override
            public WorkItemType getType() {
                return type;
            }

            @Override
            public int getPosition() {
                return position;
            }
        };
    }

    private OffsetDateTime futureOffset(int days) {
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(days);
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private void setField(Object target, String name, Object value) {
        ReflectionTestUtils.setField(target, name, value);
    }
}
