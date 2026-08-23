-- 인증 파일이 이미지인지 문서인지 조회 시점에 알 수 있도록 제출 메타데이터를 저장한다.
-- 지금은 오브젝트 키의 확장자로 추측할 수밖에 없는데, 확장자는 클라이언트가 붙인
-- 파일명에서 온 값이라 실제 내용과 다를 수 있다. 제출 검증 때 HEAD로 확정한
-- contentType을 그대로 저장해 미리보기·요약 분기의 근거로 쓴다.
ALTER TABLE todo_work_items
    ADD COLUMN proof_content_type VARCHAR(100) NULL,
    ADD COLUMN proof_file_name VARCHAR(255) NULL;
