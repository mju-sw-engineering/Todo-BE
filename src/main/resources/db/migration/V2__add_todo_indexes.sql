-- 만료 스케줄러(status=IN_PROGRESS AND deadline < now)와 팀별 기간 조회 성능을 위한 인덱스.

CREATE INDEX idx_todos_status_deadline ON todos (status, deadline);
CREATE INDEX idx_todos_team_deadline ON todos (team_id, deadline);
