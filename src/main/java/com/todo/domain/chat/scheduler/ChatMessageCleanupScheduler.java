package com.todo.domain.chat.scheduler;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class ChatMessageCleanupScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamChatMessageRepository teamChatMessageRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(7);
        teamChatMessageRepository.deleteByCreatedAtBefore(cutoff);
    }
}
