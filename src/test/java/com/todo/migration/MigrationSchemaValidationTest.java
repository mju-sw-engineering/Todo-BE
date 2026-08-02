package com.todo.migration;

import com.todo.support.MySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Flyway 마이그레이션이 만든 스키마가 엔티티 매핑과 일치하는지 검증한다.
 *
 * <p>기본 테스트 환경은 Flyway를 끄고 H2에서 엔티티로 스키마를 생성한다. 스키마가 엔티티에서
 * 나오므로 둘이 어긋날 수가 없고, 결과적으로 마이그레이션과 엔티티의 drift를 전혀 잡지 못한다.
 * 실제로 {@code V13}이 시각 컬럼을 TINYINT로 만들고 엔티티는 int를 쓰는 불일치가 모든 테스트를
 * 통과한 뒤 운영 배포에서 기동 실패로 드러났다.
 *
 * <pre>
 * Schema-validation: wrong column type encountered in column [end_hour]
 * in table [availability_polls]; found [tinyint], but expecting [integer]
 * </pre>
 *
 * <p>여기서는 실제 MySQL에 마이그레이션을 V1부터 순서대로 적용하고
 * {@code ddl-auto: validate}로 컨텍스트를 띄운다. 불일치가 있으면 컨텍스트 로딩이 실패하므로
 * 별도 단언이 필요 없다. 새 마이그레이션을 추가할 때 이 테스트가 통과하면 운영에서도 뜬다.
 *
 * <p>Hibernate는 첫 불일치에서 검증을 중단한다. 실패 메시지에 나온 컬럼만 고치면 다음 불일치로
 * 옮겨갈 뿐이므로, 고친 뒤 반드시 다시 돌려야 한다.
 */
@SpringBootTest
class MigrationSchemaValidationTest extends MySqlTestSupport {

    @Test
    void 마이그레이션_스키마가_엔티티_매핑과_일치한다() {
        // 컨텍스트가 뜨는 것 자체가 검증이다.
        // mysqltest 프로파일이 flyway.enabled=true, ddl-auto=validate를 켠다.
    }
}
