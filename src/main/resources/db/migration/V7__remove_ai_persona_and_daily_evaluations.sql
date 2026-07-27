-- AI 페르소나(ANGEL/DEVIL)와 하루 평가 기능 제거
DROP TABLE IF EXISTS daily_evaluations;

ALTER TABLE teams DROP COLUMN ai_persona;
