-- 진행 중인 WorkItem에 "오늘도 했어요"를 남기는 체크인.
-- 같은 항목에는 하루 1번만 가능하다 (uq_wici). 잔디/팀 리듬 집계의 "진행을 남김" 근거가 된다.
-- 사용자 탈퇴(행 삭제)와 WorkItem 삭제 경로(탈퇴 시 진행 중 DIRECT 삭제, 팀 삭제)가 있어
-- 두 FK 모두 ON DELETE CASCADE로 정리한다.
CREATE TABLE work_item_check_ins (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_item_id BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    check_date   DATE         NOT NULL,
    memo         VARCHAR(100) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uq_wici           UNIQUE (work_item_id, user_id, check_date),
    CONSTRAINT fk_wici_work_item FOREIGN KEY (work_item_id) REFERENCES todo_work_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_wici_user      FOREIGN KEY (user_id)      REFERENCES users(id)           ON DELETE CASCADE
);

-- 나의 잔디 조회가 (user_id, check_date) 범위 스캔이다.
CREATE INDEX idx_wici_user_date ON work_item_check_ins (user_id, check_date);

-- 피드 집계가 제출 시각으로 기간 필터를 건다. 없으면 todo_work_items full scan.
CREATE INDEX idx_twi_submitted_at ON todo_work_items (submitted_at);
