-- 인증 파일 AI 판정 결과이자 재시도 큐. 두 역할을 한 테이블에 합친다.
-- work item과 1:1이라 큐를 분리하면 결과 조회마다 조인만 늘어난다.
--
-- 제출 트랜잭션은 PENDING 한 줄만 남기고 즉시 끝난다. 실제 OpenAI 호출은 폴러가
-- 별도 트랜잭션으로 처리하므로, OpenAI 장애가 제출·조회를 막지 않는다.
CREATE TABLE proof_ai_analyses (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_item_id    BIGINT       NOT NULL,
    input_kind      VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    verdict         VARCHAR(20)  NULL,
    summary         VARCHAR(600) NULL,
    -- 제출자 본인에게만 노출된다. 팀 브로드캐스트에는 절대 싣지 않는다.
    mismatch_reason VARCHAR(300) NULL,
    model           VARCHAR(50)  NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    CONSTRAINT uq_proof_ai_analyses_work_item UNIQUE (work_item_id),
    CONSTRAINT fk_proof_ai_analyses_work_item FOREIGN KEY (work_item_id)
        REFERENCES todo_work_items (id) ON DELETE CASCADE,
    -- 폴러가 매 tick마다 "처리 대상"을 찾는 유일한 조회 경로다.
    INDEX idx_proof_ai_analyses_status_next (status, next_attempt_at)
);
