package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TeamRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    void 성공_카운트와_연속_카운트를_원자적으로_증가시킨다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        entityManager.flush();

        teamRepository.incrementSuccessCount(team.getId());

        Team updated = entityManager.find(Team.class, team.getId());
        entityManager.refresh(updated);
        assertThat(updated.getSuccessCount()).isEqualTo(1);
        assertThat(updated.getConsecutiveTodoCount()).isEqualTo(1);
    }
}
