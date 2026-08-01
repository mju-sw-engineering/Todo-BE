package com.todo.global.file.repository;

import com.todo.global.file.entity.FileDeletionOutbox;
import com.todo.global.file.entity.FileDeletionOutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FileDeletionOutboxRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileDeletionOutboxRepository repository;

    @Test
    void 삭제가능한_PENDING_행만_다음시도시각순으로_조회한다() {
        LocalDateTime now = LocalDateTime.now();
        FileDeletionOutbox ready = persist(pending("ready", now.minusSeconds(10)));
        persist(pending("future", now.plusMinutes(5)));
        FileDeletionOutbox deleted = persist(pending("deleted", now.minusSeconds(10)));
        deleted.markDeleted();
        entityManager.flush();

        List<Long> ids = repository.findDispatchableIds(now, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(ready.getId());
    }

    @Test
    void 보존기간이_지난_DELETED_행만_삭제한다() {
        FileDeletionOutbox oldDeleted = persist(pending("old", LocalDateTime.now()));
        oldDeleted.markDeleted();
        FileDeletionOutbox recentDeleted = persist(pending("recent", LocalDateTime.now()));
        recentDeleted.markDeleted();
        persist(pending("pending", LocalDateTime.now()));
        entityManager.flush();
        ageUpdatedAt(oldDeleted.getId(), LocalDateTime.now().minusDays(10));
        entityManager.clear();

        int deleted = repository.deleteByStatusInAndUpdatedAtBefore(
                List.of(FileDeletionOutboxStatus.DELETED),
                LocalDateTime.now().minusDays(7)
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(oldDeleted.getId())).isEmpty();
        assertThat(repository.findById(recentDeleted.getId())).isPresent();
    }

    private void ageUpdatedAt(Long id, LocalDateTime updatedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE file_deletion_outbox SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private FileDeletionOutbox pending(String key, LocalDateTime nextAttemptAt) {
        return FileDeletionOutbox.create(key, nextAttemptAt);
    }

    private FileDeletionOutbox persist(FileDeletionOutbox outbox) {
        return entityManager.persist(outbox);
    }
}
