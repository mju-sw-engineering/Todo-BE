package com.todo.domain.chat.scheduler;

import com.todo.domain.chat.service.ChatMessageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 보관 기간이 지난 팀 채팅 메시지를 정리한다.
 *
 * <p>스케줄러 자신은 트랜잭션 없이 루프만 돌고, 실제 삭제는 배치마다
 * {@link ChatMessageCleanupService}의 개별 트랜잭션에서 수행해 락과 커넥션 점유 시간을 짧게 유지한다.
 *
 * <p>분산 락이 없으므로 앱 인스턴스가 단일이라는 전제에 의존한다.
 * (docker-compose.prod.yml의 app 서비스는 8080 포트를 호스트에 직접 바인딩해 단일 인스턴스로 뜬다.)
 * 인스턴스를 늘리면 여러 노드가 같은 행을 동시에 지우려 해 락 경합이 생기므로,
 * 그 시점에 분산 락이나 단일 노드 실행 보장을 함께 도입해야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageCleanupScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 1000;

    private final ChatMessageCleanupService chatMessageCleanupService;

    @Value("${chat.cleanup.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${chat.cleanup.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void cleanupOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(retentionDays);

        int total = 0;
        int deleted;
        do {
            deleted = chatMessageCleanupService.deleteBatch(cutoff, BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);

        if (total > 0) {
            log.info(
                    "보관 기간이 지난 채팅 메시지 정리 완료. retentionDays={}, deletedCount={}",
                    retentionDays,
                    total
            );
        }
    }
}
