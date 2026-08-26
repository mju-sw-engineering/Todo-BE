package com.todo.global.file.repository;

import com.todo.global.file.entity.UploadLedger;
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
class UploadLedgerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UploadLedgerRepository repository;

    @Test
    void 유예가_지난_행만_id_오름차순으로_조회한다() {
        LocalDateTime now = LocalDateTime.now();
        UploadLedger expired1 = persistWithCreatedAt("proofs/1/1/1/a.jpg", now.minusHours(25));
        UploadLedger expired2 = persistWithCreatedAt("proofs/1/1/1/b.jpg", now.minusHours(30));
        persistWithCreatedAt("proofs/1/1/1/fresh.jpg", now.minusHours(1));

        List<UploadLedger> result = repository.findExpiredAfterCursor(
                now.minusHours(24), 0L, PageRequest.of(0, 10));

        assertThat(result).extracting(UploadLedger::getId)
                .containsExactly(expired1.getId(), expired2.getId());
    }

    @Test
    void 커서_이하의_행은_건너뛴다() {
        // dry-run은 행을 지우지 않으므로 커서가 전진해야 같은 행을 무한히 다시 읽지 않는다
        LocalDateTime now = LocalDateTime.now();
        UploadLedger first = persistWithCreatedAt("proofs/1/1/1/a.jpg", now.minusHours(25));
        UploadLedger second = persistWithCreatedAt("proofs/1/1/1/b.jpg", now.minusHours(25));

        List<UploadLedger> result = repository.findExpiredAfterCursor(
                now.minusHours(24), first.getId(), PageRequest.of(0, 10));

        assertThat(result).extracting(UploadLedger::getId).containsExactly(second.getId());
    }

    private UploadLedger persistWithCreatedAt(String key, LocalDateTime createdAt) {
        UploadLedger ledger = entityManager.persist(UploadLedger.create(key));
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE upload_ledger SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", ledger.getId())
                .executeUpdate();
        entityManager.refresh(ledger);
        return ledger;
    }
}
