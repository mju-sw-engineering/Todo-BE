package com.todo.global.file.service;

import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.FileStorageException;
import com.todo.global.file.config.OrphanCleanupProperties;
import com.todo.global.file.entity.UploadLedger;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.file.repository.UploadLedgerRepository;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 4, 50);

    @Mock
    private UploadLedgerRepository uploadLedgerRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private FileDeletionOutboxRepository fileDeletionOutboxRepository;
    @Mock
    private FileService fileService;

    private OrphanFileCleanupService service(boolean dryRun) {
        return new OrphanFileCleanupService(
                uploadLedgerRepository,
                todoWorkItemRepository,
                userRepository,
                teamRepository,
                fileDeletionOutboxRepository,
                fileService,
                new OrphanCleanupProperties(true, dryRun, 24, 500)
        );
    }

    private UploadLedger ledger(long id, String key) {
        UploadLedger entry = UploadLedger.create(key);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "createdAt", NOW.minusDays(2));
        return entry;
    }

    private void givenBatch(UploadLedger... entries) {
        given(uploadLedgerRepository.findExpiredAfterCursor(any(), eq(0L), any(Pageable.class)))
                .willReturn(List.of(entries));
        if (entries.length > 0) {
            long lastId = entries[entries.length - 1].getId();
            given(uploadLedgerRepository.findExpiredAfterCursor(any(), eq(lastId), any(Pageable.class)))
                    .willReturn(List.of());
        }
    }

    private void givenNoReferences() {
        given(todoWorkItemRepository.findProofImageKeysIn(anyCollection())).willReturn(List.of());
        given(todoWorkItemRepository.findProofThumbnailKeysIn(anyCollection())).willReturn(List.of());
        given(userRepository.findProfileImageKeysIn(anyCollection())).willReturn(List.of());
        given(teamRepository.findTeamImageKeysIn(anyCollection())).willReturn(List.of());
        given(fileDeletionOutboxRepository.findObjectKeysIn(anyCollection())).willReturn(List.of());
    }

    @Test
    void 어디에서도_참조되지_않는_키는_객체를_지우고_원장_행을_정리한다() {
        UploadLedger orphan = ledger(1L, "proofs/1/2/3/orphan.jpg");
        givenBatch(orphan);
        givenNoReferences();

        service(false).cleanupExpired(NOW);

        then(fileService).should().deleteObjectOrThrow("proofs/1/2/3/orphan.jpg");
        then(uploadLedgerRepository).should().delete(orphan);
    }

    @Test
    void 인증_사진으로_참조되는_키는_객체를_지우지_않고_원장_행만_정리한다() {
        UploadLedger referenced = ledger(1L, "proofs/1/2/3/live.jpg");
        givenBatch(referenced);
        givenNoReferences();
        given(todoWorkItemRepository.findProofImageKeysIn(anyCollection()))
                .willReturn(List.of("proofs/1/2/3/live.jpg"));

        service(false).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
        then(uploadLedgerRepository).should().delete(referenced);
    }

    @Test
    void temp_경로여도_프로필로_참조되면_지우지_않는다() {
        // 가입 완료 후에도 profiles/temp/ 키가 users.profile_image_url에 그대로 남는다.
        // 경로 이름으로 판단하면 살아있는 프로필이 날아간다.
        UploadLedger tempButLive = ledger(1L, "profiles/temp/live.png");
        givenBatch(tempButLive);
        givenNoReferences();
        given(userRepository.findProfileImageKeysIn(anyCollection()))
                .willReturn(List.of("profiles/temp/live.png"));

        service(false).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
        then(uploadLedgerRepository).should().delete(tempButLive);
    }

    @Test
    void 팀_이미지로_참조되는_temp_키는_지우지_않는다() {
        // teams/temp/는 임시 경로가 아니라 팀 이미지의 최종 저장 위치다.
        UploadLedger teamImage = ledger(1L, "teams/temp/1/live.png");
        givenBatch(teamImage);
        givenNoReferences();
        given(teamRepository.findTeamImageKeysIn(anyCollection()))
                .willReturn(List.of("teams/temp/1/live.png"));

        service(false).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
    }

    @Test
    void 삭제_outbox에_이미_올라간_키는_이중_처리하지_않는다() {
        UploadLedger queued = ledger(1L, "proofs/1/2/3/queued.jpg");
        givenBatch(queued);
        givenNoReferences();
        given(fileDeletionOutboxRepository.findObjectKeysIn(anyCollection()))
                .willReturn(List.of("proofs/1/2/3/queued.jpg"));

        service(false).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
        then(uploadLedgerRepository).should().delete(queued);
    }

    @Test
    void 배치_조회_후_삭제_직전에_참조가_생기면_지우지_않는다() {
        UploadLedger raced = ledger(1L, "proofs/1/2/3/raced.jpg");
        UploadLedger orphan = ledger(2L, "proofs/1/2/3/orphan.jpg");
        givenBatch(raced, orphan);
        givenNoReferences();
        // 배치 조회(키 2개)에서는 미참조였다가 삭제 직전 재확인(키 1개)에서 참조로 바뀐 상황 —
        // 그 사이 제출이 끼어든 레이스를 재현한다
        given(todoWorkItemRepository.findProofImageKeysIn(
                argThat((Collection<String> keys) ->
                        keys != null && keys.size() == 1 && keys.contains("proofs/1/2/3/raced.jpg"))))
                .willReturn(List.of("proofs/1/2/3/raced.jpg"));

        service(false).cleanupExpired(NOW);

        // raced는 파일을 남기고 행만 정리, orphan은 정상 삭제
        then(fileService).should(never()).deleteObjectOrThrow("proofs/1/2/3/raced.jpg");
        then(fileService).should().deleteObjectOrThrow("proofs/1/2/3/orphan.jpg");
        then(uploadLedgerRepository).should().delete(raced);
        then(uploadLedgerRepository).should().delete(orphan);
    }

    @Test
    void dry_run에서는_아무것도_지우지_않고_로그만_남긴다() {
        UploadLedger orphan = ledger(1L, "proofs/1/2/3/orphan.jpg");
        UploadLedger referenced = ledger(2L, "profiles/1/live.png");
        givenBatch(orphan, referenced);
        givenNoReferences();
        given(userRepository.findProfileImageKeysIn(anyCollection()))
                .willReturn(List.of("profiles/1/live.png"));

        service(true).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
        then(uploadLedgerRepository).should(never()).delete(any(UploadLedger.class));
    }

    @Test
    void 객체_삭제가_실패하면_원장_행을_남겨_다음_실행에서_재시도한다() {
        UploadLedger orphan = ledger(1L, "proofs/1/2/3/orphan.jpg");
        givenBatch(orphan);
        givenNoReferences();
        doThrow(new FileStorageException("파일 삭제에 실패했습니다.", new RuntimeException()))
                .when(fileService).deleteObjectOrThrow("proofs/1/2/3/orphan.jpg");

        service(false).cleanupExpired(NOW);

        then(uploadLedgerRepository).should(never()).delete(any(UploadLedger.class));
    }

    @Test
    void 유예_기간이_지난_행이_없으면_아무_일도_하지_않는다() {
        given(uploadLedgerRepository.findExpiredAfterCursor(any(), anyLong(), any(Pageable.class)))
                .willReturn(List.of());

        service(false).cleanupExpired(NOW);

        then(fileService).should(never()).deleteObjectOrThrow(any());
        then(todoWorkItemRepository).should(never()).findProofImageKeysIn(anyCollection());
    }
}
