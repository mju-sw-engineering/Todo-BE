-- 팀 채팅 슬래시 명령어(#193) 실행 결과 저장 테이블.
--
-- command/status는 VARCHAR로 둔다. notifications.type이 네이티브 MySQL ENUM이라 새 종류를
-- 추가할 때마다 컬럼 정의를 다시 나열하는 마이그레이션이 필요했고(V20260813202743), 그 실수를
-- 반복하지 않기 위함이다.
--
-- executor_id는 회원 탈퇴 하드 딜리트 정책(V10)을 따른다: FK는 RESTRICT로 두고 NULL만 허용해
-- 애플리케이션이 명시적으로 정리하게 한다. chat_message_id는 반대로 CASCADE다 — 실행 결과는
-- 그 메시지(칩)에 종속된 자식 레코드라 메시지가 채팅 정리 스케줄러로 삭제되면 같이 사라지는 게
-- 맞다(availability_poll_dates, work_item_check_ins와 같은 결).

CREATE TABLE slash_command_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    executor_id BIGINT NULL,
    chat_message_id BIGINT NOT NULL,
    command VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json TEXT NULL,
    executed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sce_chat_message (chat_message_id),
    KEY idx_sce_executor (executor_id),
    CONSTRAINT fk_sce_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_sce_executor FOREIGN KEY (executor_id) REFERENCES users (id),
    CONSTRAINT fk_sce_chat_message FOREIGN KEY (chat_message_id) REFERENCES team_chat_messages (id) ON DELETE CASCADE
) ENGINE=InnoDB;
