-- 가능 시간 투표 테이블의 시각 컬럼이 TINYINT로 생성되어 Hibernate 스키마 검증이 실패한다.
-- 엔티티는 int(=INTEGER)를 쓰는데 V13이 TINYINT로 만들어, prod의 ddl-auto: validate가
-- 기동을 막는다.
--
--   Schema-validation: wrong column type encountered in column [end_hour]
--   in table [availability_polls]; found [tinyint], but expecting [integer]
--
-- 검증은 첫 불일치에서 중단되므로 에러에는 end_hour만 나오지만 start_hour와
-- availability_slots.hour도 같은 문제다. 셋을 함께 고쳐야 한다.
--
-- 엔티티를 byte로 바꾸거나 @JdbcTypeCode(TINYINT)를 붙이는 방법도 있지만,
-- 컬럼 타입을 Hibernate의 기본 매핑에 맞추는 쪽을 택했다. 애노테이션 방식은
-- 앞으로 시각 컬럼을 추가할 때마다 잊지 않아야 하고, 잊으면 다시 기동 실패로 나타난다.
--
-- V13 적용 직후 이 검증에서 기동이 막혀 앱이 한 번도 뜨지 못했으므로 세 테이블은 비어 있다.

ALTER TABLE availability_polls
    MODIFY COLUMN start_hour INT NOT NULL,
    MODIFY COLUMN end_hour INT NOT NULL;

ALTER TABLE availability_slots
    MODIFY COLUMN hour INT NOT NULL;
