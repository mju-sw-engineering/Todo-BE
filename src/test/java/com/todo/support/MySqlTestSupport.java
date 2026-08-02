package com.todo.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL이 필요한 테스트의 기반 클래스.
 *
 * <p>기본 테스트 환경은 H2 + {@code ddl-auto: create-drop}이라 다음을 검증할 수 없다.
 * <ul>
 *   <li>Flyway 마이그레이션이 실제로 적용되는지 — 기본 프로파일은 Flyway가 꺼져 있다</li>
 *   <li>생성 컬럼·복합 FK 같은 MySQL 전용 제약</li>
 *   <li>REPEATABLE READ 아래의 동시성 — H2의 MVCC는 동작이 다르다</li>
 *   <li>엔티티와 마이그레이션 스키마의 drift — 엔티티로 스키마를 만들면 어긋나도 드러나지 않는다</li>
 * </ul>
 *
 * <p>컨테이너는 JVM당 하나만 띄우고 재사용한다. {@code @Container}로 클래스마다
 * 시작·종료하면 MySQL 기동 비용(수 초)이 테스트 클래스 수만큼 곱해진다.
 * 정리는 Testcontainers의 Ryuk이 담당한다.
 *
 * <p>{@code mysql} 태그가 붙으므로 {@code ./gradlew test}에서는 제외되고
 * {@code ./gradlew mysqlTest}로만 실행된다. Docker가 필요하다.
 */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
public abstract class MySqlTestSupport {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static {
        MYSQL.start();
    }
}
