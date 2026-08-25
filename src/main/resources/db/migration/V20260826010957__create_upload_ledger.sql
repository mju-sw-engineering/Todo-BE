-- presign 발급 원장(#고아 파일 정리). presigned PUT URL을 발급할 때마다 한 행을 남기고,
-- 새벽 스케줄러가 유예 시간이 지난 행을 걷어 DB 어디에서도 참조되지 않는 객체를 삭제한다.
--
-- 버킷 전체를 스캔하는 대신 "우리가 발급한 키"만 후보로 삼기 위한 테이블이다. 원장에 없는
-- 객체는 정리 대상 자체가 되지 않으므로, 스캔 누락으로 살아있는 파일을 지우는 사고가
-- 구조적으로 불가능하다.
--
-- object_key 길이는 file_deletion_outbox.object_key(1024)와 맞춘다.
-- 인덱스는 created_at 단독 — 스케줄러가 "유예 지난 행"을 키셋 페이지네이션으로 걷는다.

CREATE TABLE upload_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(1024) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_upload_ledger_created_at (created_at)
) ENGINE=InnoDB;
