package com.todo.global.file.service;

import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.file.config.OrphanCleanupProperties;
import com.todo.global.file.entity.UploadLedger;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.file.repository.UploadLedgerRepository;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 업로드만 되고 어디에서도 참조되지 않는 파일(고아)을 정리한다.
 *
 * <p>후보는 버킷 스캔이 아니라 presign 발급 원장({@link UploadLedger})에서만 나온다.
 * 원장에 없는 객체는 이 서비스의 눈에 들어오지 않으므로, 참조 검사에 누락된 저장 경로가
 * 생겨도 그 경로의 파일이 지워지는 일은 없다.
 *
 * <p>삭제 조건은 전부 AND다:
 * <ol>
 *   <li>원장에 있고, 발급 후 유예 시간이 지났다</li>
 *   <li>DB 참조가 없다 — 인증 원본·썸네일, 프로필, 팀 이미지, 삭제 outbox 5곳을 조회한다.
 *       경로에 {@code temp/}가 있어도 참조 여부로만 판단한다. 프로필은 가입 완료 후에도
 *       {@code profiles/temp/} 키를 그대로 쓰고, 팀 이미지는 {@code teams/temp/}가 아예
 *       최종 저장 위치라 경로 이름으로 지우면 살아있는 파일이 날아간다</li>
 *   <li>삭제 직전 같은 검사를 한 번 더 통과했다 — 배치 조회와 삭제 사이에 제출이
 *       끼어드는 레이스를 밀리초 수준으로 좁힌다</li>
 * </ol>
 *
 * <p>객체 삭제에 실패하면 원장 행을 남긴다. 행이 남아 있으면 다음 실행이 다시 시도하므로
 * 원장 행 자체가 재시도 큐 역할을 한다 — 별도 백오프나 실패 상태가 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanFileCleanupService {

    private final UploadLedgerRepository uploadLedgerRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final FileDeletionOutboxRepository fileDeletionOutboxRepository;
    private final FileService fileService;
    private final OrphanCleanupProperties properties;

    /**
     * 유예 시간이 지난 원장 행을 걷어 정리한다. 새벽 스케줄러가 하루 한 번 호출한다.
     *
     * <p>의도적으로 트랜잭션을 걸지 않는다. 배치 하나에 S3 삭제가 수백 번 낄 수 있어
     * 트랜잭션으로 묶으면 커넥션을 그 시간 내내 잡는다. 원장 행 삭제는 건별로 커밋되고,
     * 어중간하게 끊겨도 남은 행은 다음 실행이 처리한다.
     */
    public void cleanupExpired(LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(properties.graceHours());
        boolean dryRun = properties.dryRun();

        int scanned = 0;
        int referenced = 0;
        int deleted = 0;
        int failed = 0;
        long cursor = 0L;

        while (true) {
            List<UploadLedger> batch = uploadLedgerRepository.findExpiredAfterCursor(
                    threshold, cursor, PageRequest.of(0, properties.batchSize()));
            if (batch.isEmpty()) {
                break;
            }
            cursor = batch.get(batch.size() - 1).getId();
            scanned += batch.size();

            Set<String> referencedKeys = findReferencedKeys(
                    batch.stream().map(UploadLedger::getObjectKey).distinct().toList());

            for (UploadLedger entry : batch) {
                if (referencedKeys.contains(entry.getObjectKey())) {
                    referenced++;
                    if (!dryRun) {
                        // 키가 살아있으니 원장의 역할은 끝났다. 실제 파일 삭제는 도메인 로직과
                        // 삭제 outbox의 몫이다.
                        uploadLedgerRepository.delete(entry);
                    }
                    continue;
                }
                if (dryRun) {
                    deleted++;
                    log.info("[dry-run] 고아 파일 삭제 대상. objectKey={}, uploadedAt={}",
                            entry.getObjectKey(), entry.getCreatedAt());
                    continue;
                }
                if (deleteIfStillOrphan(entry)) {
                    deleted++;
                } else {
                    failed++;
                }
            }
        }

        if (scanned > 0 || failed > 0) {
            log.info("고아 파일 정리 완료. dryRun={}, scanned={}, referenced={}, deleted={}, failed={}",
                    dryRun, scanned, referenced, deleted, failed);
        }
    }

    /**
     * 삭제 직전 참조를 재확인하고 지운다. 배치 조회 시점과의 간격 동안 제출이 끼어들었다면
     * 여기서 걸러진다. 재확인마저 통과한 뒤의 잔여 레이스는 제출 경로의 HEAD 검증이
     * "파일 없음" 오류로 표면화하므로 조용한 데이터 손상으로는 이어지지 않는다.
     *
     * @return 원장 행이 정리됐으면 true, 다음 실행으로 미뤘으면 false
     */
    private boolean deleteIfStillOrphan(UploadLedger entry) {
        if (!findReferencedKeys(List.of(entry.getObjectKey())).isEmpty()) {
            uploadLedgerRepository.delete(entry);
            return true;
        }
        try {
            fileService.deleteObjectOrThrow(entry.getObjectKey());
        } catch (RuntimeException e) {
            // 행을 남겨 다음 실행에서 재시도한다.
            log.warn("고아 파일 삭제 실패. objectKey={}", entry.getObjectKey(), e);
            return false;
        }
        log.info("고아 파일 삭제. objectKey={}, uploadedAt={}", entry.getObjectKey(), entry.getCreatedAt());
        uploadLedgerRepository.delete(entry);
        return true;
    }

    /** 후보 키 중 DB 어딘가에서 참조 중인 키를 돌려준다. 저장 경로가 늘면 여기에 추가한다. */
    private Set<String> findReferencedKeys(Collection<String> keys) {
        Set<String> referenced = new HashSet<>();
        referenced.addAll(todoWorkItemRepository.findProofImageKeysIn(keys));
        referenced.addAll(todoWorkItemRepository.findProofThumbnailKeysIn(keys));
        referenced.addAll(userRepository.findProfileImageKeysIn(keys));
        referenced.addAll(teamRepository.findTeamImageKeysIn(keys));
        referenced.addAll(fileDeletionOutboxRepository.findObjectKeysIn(keys));
        return referenced;
    }
}
