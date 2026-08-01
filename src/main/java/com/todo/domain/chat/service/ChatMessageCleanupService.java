package com.todo.domain.chat.service;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 오래된 채팅 메시지 삭제의 트랜잭션 경계. 스케줄러와 별도 빈으로 둔 이유는
 * 같은 클래스 안에서 호출하면 프록시를 타지 않아 배치마다 트랜잭션이 열리지 않기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageCleanupService {

    private final TeamChatMessageRepository teamChatMessageRepository;

    /**
     * 배치 하나를 독립 트랜잭션으로 삭제한다. 중간 배치에서 실패해도 이미 커밋된 배치는 유지되며,
     * 정리 작업은 다음 회차에 이어서 지우면 되므로 전체 롤백보다 바람직하다.
     */
    @Transactional
    public int deleteBatch(LocalDateTime cutoff, int batchSize) {
        return teamChatMessageRepository.deleteBatchCreatedBefore(cutoff, batchSize);
    }
}
