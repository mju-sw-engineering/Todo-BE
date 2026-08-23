package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.service.FileService;
import com.todo.support.MySqlTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서로 다른 담당자가 마지막 두 WorkItem을 동시에 제출하는 상황을 검증한다.
 *
 * <p>WorkItem 행만 잠그면 두 트랜잭션이 서로 다른 행을 잠그므로 아무것도 직렬화되지 않는다.
 * MySQL 기본 REPEATABLE READ에서 각 트랜잭션은 상대의 미커밋 제출을 보지 못하고,
 * 양쪽 모두 "아직 전부 성공이 아니다"로 판정한다. 결과적으로 WorkItem은 전부 SUCCESS인데
 * Todo는 IN_PROGRESS로 남는다.
 *
 * <p>H2에서는 격리 수준 동작이 달라 재현되지 않으므로 실제 MySQL이 필요하다.
 */
@SpringBootTest
class TodoSubmitConcurrencyTest extends MySqlTestSupport {

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private TodoWorkItemRepository todoWorkItemRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private FileService fileService;

    private Team team;
    private User first;
    private User second;

    @BeforeEach
    void setUp() {
        team = teamRepository.save(Team.create("동시성팀", null, "INVITE-" + System.nanoTime()));
        first = userRepository.save(User.create("first-" + System.nanoTime(), "pw", "윤진", null));
        second = userRepository.save(User.create("second-" + System.nanoTime(), "pw", "종혁", null));
        teamMemberRepository.save(TeamMember.create(team, first, TeamMemberRole.LEADER));
        teamMemberRepository.save(TeamMember.create(team, second, TeamMemberRole.MEMBER));
    }

    @AfterEach
    void tearDown() {
        // 제출이 팀원에게 알림을 남기고, 알림은 actor_id로 users를 참조한다.
        // 먼저 지우지 않으면 users 삭제가 FK에 막힌다.
        notificationRepository.deleteAll();
        todoWorkItemRepository.deleteAll();
        todoRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 마지막_두_WorkItem을_동시에_제출해도_Todo는_SUCCESS가_되고_성공_횟수는_정확히_1이다() throws Exception {
        TodoWorkItemIds workItemIds = createTodoWithBothWorkItems("동시 제출");

        submitConcurrently(workItemIds);

        Todo completedTodo = todoRepository.findById(workItemIds.todoId()).orElseThrow();
        Team completedTeam = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(completedTodo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        assertThat(completedTeam.getSuccessCount()).isEqualTo(1);
    }

    private TodoWorkItemIds createTodoWithBothWorkItems(String title) {
        Todo todo = todoRepository.save(
                Todo.create(team, first, title, null, LocalDateTime.now().plusDays(1))
        );
        TodoWorkItem firstWorkItem = todoWorkItemRepository.save(TodoWorkItem.createDirect(todo, first));
        TodoWorkItem secondWorkItem = todoWorkItemRepository.save(TodoWorkItem.createDirect(todo, second));
        return new TodoWorkItemIds(todo.getId(), firstWorkItem.getId(), secondWorkItem.getId());
    }

    /**
     * 두 담당자를 같은 순간에 출발시킨다. 트랜잭션 내부에 개입하지 않고 시작 시점만 맞춘다 —
     * 내부에 래치를 넣으면 락이 추가된 뒤에는 그 래치가 데드락이 되어 수정 전후를 같은
     * 테스트로 비교할 수 없다.
     */
    private void submitConcurrently(TodoWorkItemIds workItemIds) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> failure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (Submission submission : List.of(
                    new Submission(first, workItemIds.firstWorkItemId()),
                    new Submission(second, workItemIds.secondWorkItemId())
            )) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        todoService.submitTodoWorkItem(
                                submission.workItemId(),
                                String.valueOf(submission.submitter().getId()),
                                new SubmitTodoRequest("proofs/" + submission.submitter().getId()
                                        + "/" + workItemIds.todoId() + ".jpg", null)
                        );
                    } catch (Exception e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private record TodoWorkItemIds(Long todoId, Long firstWorkItemId, Long secondWorkItemId) {
    }

    private record Submission(User submitter, Long workItemId) {
    }
}
