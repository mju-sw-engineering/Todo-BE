package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서로 다른 담당자가 마지막 두 건을 동시에 제출하는 상황을 검증한다.
 *
 * <p>참가자 행만 잠그면 두 트랜잭션이 서로 다른 행을 잠그므로 아무것도 직렬화되지 않는다.
 * MySQL 기본 REPEATABLE READ에서 각 트랜잭션은 상대의 미커밋 제출을 보지 못하고,
 * 양쪽 모두 "아직 전부 성공이 아니다"로 판정한다. 결과적으로 참가자는 전부 SUCCESS인데
 * Todo는 IN_PROGRESS로 남는다.
 *
 * <p>H2에서는 격리 수준 동작이 달라 재현되지 않으므로 실제 MySQL이 필요하다.
 */
@SpringBootTest
class TodoSubmitConcurrencyTest extends MySqlTestSupport {

    private static final int RACE_ROUNDS = 10;

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private TodoParticipantRepository todoParticipantRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

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
        todoParticipantRepository.deleteAll();
        todoRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 마지막_두_담당자가_동시에_제출해도_투두는_SUCCESS가_된다() throws Exception {
        List<Long> todoIds = new ArrayList<>();
        for (int round = 0; round < RACE_ROUNDS; round++) {
            todoIds.add(createTodoWithBothParticipants("동시 제출 " + round));
        }

        for (Long todoId : todoIds) {
            submitConcurrently(todoId);
        }

        List<Long> stuck = todoIds.stream()
                .filter(id -> todoRepository.findById(id).orElseThrow().getStatus() != TodoStatus.SUCCESS)
                .toList();

        assertThat(stuck)
                .describedAs("모든 담당자가 제출했는데 IN_PROGRESS로 남은 Todo — 동시 제출 상태 유실")
                .isEmpty();
    }

    private Long createTodoWithBothParticipants(String title) {
        Todo todo = todoRepository.save(
                Todo.create(team, first, title, null, LocalDateTime.now().plusDays(1))
        );
        todoParticipantRepository.save(TodoParticipant.create(todo, first));
        todoParticipantRepository.save(TodoParticipant.create(todo, second));
        return todo.getId();
    }

    /**
     * 두 담당자를 같은 순간에 출발시킨다. 트랜잭션 내부에 개입하지 않고 시작 시점만 맞춘다 —
     * 내부에 래치를 넣으면 락이 추가된 뒤에는 그 래치가 데드락이 되어 수정 전후를 같은
     * 테스트로 비교할 수 없다.
     */
    private void submitConcurrently(Long todoId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> failure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (User submitter : List.of(first, second)) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        todoService.submitTodo(
                                todoId,
                                submitter.getLoginId(),
                                new SubmitTodoRequest("proofs/" + submitter.getId() + "/" + todoId + ".jpg")
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
}
