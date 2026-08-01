package com.todo.domain.todo.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoParticipantRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoParticipantRepository todoParticipantRepository;

    @Test
    void 마감이_지난_투두의_진행중_참가자만_FAIL로_바꾼다() {
        LocalDateTime now = LocalDateTime.now();
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        User memberA = entityManager.persist(User.create("loginA", "pw", "닉A", null));
        User memberB = entityManager.persist(User.create("loginB", "pw", "닉B", null));

        Todo expiredTodo = entityManager.persist(Todo.create(team, memberA, "만료 투두", null, now.minusHours(1)));
        Todo activeTodo = entityManager.persist(Todo.create(team, memberA, "진행 투두", null, now.plusHours(1)));

        TodoParticipant expiredInProgress = entityManager.persist(TodoParticipant.create(expiredTodo, memberA));
        TodoParticipant expiredSuccess = TodoParticipant.create(expiredTodo, memberB);
        expiredSuccess.markAsSuccess();
        entityManager.persist(expiredSuccess);
        TodoParticipant activeInProgress = entityManager.persist(TodoParticipant.create(activeTodo, memberA));
        entityManager.flush();

        int updated = todoParticipantRepository.markExpiredParticipantsAsFail(now);

        assertThat(updated).isEqualTo(1);
        assertThat(entityManager.find(TodoParticipant.class, expiredInProgress.getId()).getStatus())
                .isEqualTo(ParticipantStatus.FAIL);
        assertThat(entityManager.find(TodoParticipant.class, expiredSuccess.getId()).getStatus())
                .isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(entityManager.find(TodoParticipant.class, activeInProgress.getId()).getStatus())
                .isEqualTo(ParticipantStatus.IN_PROGRESS);
    }

    @Test
    void 팀삭제용_인증사진_키는_대상_투두의_null이_아닌_키만_조회한다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE2"));
        User member = entityManager.persist(User.create("member", "pw", "닉", null));
        User memberWithoutFiles = entityManager.persist(User.create("member2", "pw", "닉2", null));
        Todo target = entityManager.persist(Todo.create(
                team,
                member,
                "대상",
                null,
                LocalDateTime.now().plusHours(1)
        ));
        Todo other = entityManager.persist(Todo.create(
                team,
                member,
                "다른 투두",
                null,
                LocalDateTime.now().plusHours(1)
        ));
        TodoParticipant withFiles = TodoParticipant.create(target, member);
        withFiles.submit("proofs/original.png", "proofs/thumb.jpg");
        entityManager.persist(withFiles);
        entityManager.persist(TodoParticipant.create(target, memberWithoutFiles));
        TodoParticipant otherFiles = TodoParticipant.create(other, member);
        otherFiles.submit("proofs/other.png", "proofs/other-thumb.jpg");
        entityManager.persist(otherFiles);
        entityManager.flush();

        assertThat(todoParticipantRepository.findProofImageKeysByTodoIdIn(List.of(target.getId())))
                .containsExactly("proofs/original.png");
        assertThat(todoParticipantRepository.findProofThumbnailKeysByTodoIdIn(List.of(target.getId())))
                .containsExactly("proofs/thumb.jpg");
    }
}
