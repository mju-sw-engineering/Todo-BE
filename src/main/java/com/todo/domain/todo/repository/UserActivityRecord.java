package com.todo.domain.todo.repository;

import java.time.LocalDateTime;

/**
 * 피드 집계용 활동 이벤트 한 건. 발생 시각(KST 저장값)과 행위자, 대상 투두를 담는다.
 */
public interface UserActivityRecord {

    LocalDateTime getOccurredAt();

    Long getUserId();

    Long getTodoId();
}
