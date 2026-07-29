package com.todo.domain.chat.repository;

import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TeamChatMessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TeamChatMessageRepository teamChatMessageRepository;

    @Test
    void cutoff_이전_메시지만_삭제한다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        User sender = entityManager.persist(User.create("user1", "password", "나", null));
        Long old = entityManager.persist(TeamChatMessage.create(team, sender, "오래된 메시지")).getId();
        Long recent = entityManager.persist(TeamChatMessage.create(team, sender, "최근 메시지")).getId();
        entityManager.flush();
        ageCreatedAt(old, LocalDateTime.now().minusDays(10));
        entityManager.clear();

        int deleted = teamChatMessageRepository.deleteBatchCreatedBefore(LocalDateTime.now().minusDays(7), 1000);

        assertThat(deleted).isEqualTo(1);
        assertThat(teamChatMessageRepository.findById(old)).isEmpty();
        assertThat(teamChatMessageRepository.findById(recent)).isPresent();
    }

    @Test
    void 삭제는_limit_건수를_넘지_않는다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        User sender = entityManager.persist(User.create("user1", "password", "나", null));
        for (int i = 0; i < 3; i++) {
            Long id = entityManager.persist(TeamChatMessage.create(team, sender, "메시지" + i)).getId();
            entityManager.flush();
            ageCreatedAt(id, LocalDateTime.now().minusDays(10));
        }
        entityManager.clear();

        int deleted = teamChatMessageRepository.deleteBatchCreatedBefore(LocalDateTime.now().minusDays(7), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(teamChatMessageRepository.count()).isEqualTo(1);
    }

    private void ageCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE team_chat_messages SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
