-- 팀 소개(설명) 필드. 팀 생성 시 선택 입력, 목록·상세에 노출된다.
ALTER TABLE teams
    ADD COLUMN description VARCHAR(255) NULL AFTER team_name;
