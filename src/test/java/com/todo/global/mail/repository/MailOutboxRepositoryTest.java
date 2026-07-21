package com.todo.global.mail.repository;

import com.todo.global.mail.entity.MailOutbox;
import com.todo.global.mail.entity.MailOutboxStatus;
import com.todo.global.mail.entity.MailType;
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
class MailOutboxRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Test
    void 발송가능한_PENDING_메일만_다음시도시각순으로_조회한다() {
        LocalDateTime now = LocalDateTime.now();
        MailOutbox ready = persist(pending(now.minusSeconds(10)));
        MailOutbox future = persist(pending(now.plusMinutes(5)));
        MailOutbox sent = persist(pending(now.minusSeconds(10)));
        sent.markSent();
        entityManager.flush();

        List<Long> ids = mailOutboxRepository.findDispatchableIds(now, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(ready.getId());
    }

    @Test
    void 보존기간이_지난_SENT_FAILED_메일만_삭제한다() {
        MailOutbox oldSent = persist(pending(LocalDateTime.now()));
        oldSent.markSent();
        MailOutbox recentSent = persist(pending(LocalDateTime.now()));
        recentSent.markSent();
        MailOutbox recentPending = persist(pending(LocalDateTime.now()));
        entityManager.flush();
        ageUpdatedAt(oldSent.getId(), LocalDateTime.now().minusDays(10));
        entityManager.clear();

        int deleted = mailOutboxRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(MailOutboxStatus.SENT, MailOutboxStatus.FAILED),
                LocalDateTime.now().minusDays(7)
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(mailOutboxRepository.findById(oldSent.getId())).isEmpty();
        assertThat(mailOutboxRepository.findById(recentSent.getId())).isPresent();
        assertThat(mailOutboxRepository.findById(recentPending.getId())).isPresent();
    }

    private void ageUpdatedAt(Long id, LocalDateTime updatedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE mail_outbox SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private MailOutbox pending(LocalDateTime nextAttemptAt) {
        return MailOutbox.create(MailType.VERIFICATION, "user@example.com", "제목", "본문", "<p>본문</p>",
                null, 3, nextAttemptAt);
    }

    private MailOutbox persist(MailOutbox outbox) {
        return entityManager.persist(outbox);
    }
}
