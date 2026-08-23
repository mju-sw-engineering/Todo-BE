package com.todo.domain.notification.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NotificationType}과 notifications.type ENUM 컬럼이 어긋나지 않는지 지킨다.
 *
 * <p>이 둘은 두 번 어긋났다. 처음에는 팀 참여 알림이(V20260813202743), 두 번째는 인증 파일
 * 불일치 알림이 걸렸다. 증상이 고약하다 — DB가 모르는 값을 INSERT하면 "Data truncated"로
 * 실패하면서 알림을 저장하려던 <b>호출자 트랜잭션 전체</b>가 롤백된다. 알림 하나 때문에
 * 팀 참여 API가 500이 되고, AI 판정 결과가 통째로 사라졌다.
 *
 * <p>{@code ddl-auto=validate}는 컬럼 타입만 볼 뿐 ENUM의 허용값까지는 대조하지 않아
 * 기동 시점에 잡히지 않는다. 그래서 마이그레이션 파일을 직접 읽어 확인한다.
 */
class NotificationTypeSchemaTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Pattern ENUM_DEFINITION =
            Pattern.compile("MODIFY\\s+COLUMN\\s+type\\s+ENUM\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    @Test
    void 모든_알림_종류가_DB_ENUM_컬럼에_정의돼_있다() throws IOException {
        List<String> allowedValues = latestNotificationTypeEnumValues();

        assertThat(allowedValues)
                .as("NotificationType에 값을 추가했다면 notifications.type ENUM도 함께 넓혀야 한다")
                .containsAll(Arrays.stream(NotificationType.values()).map(Enum::name).toList());
    }

    /** 가장 나중에 적용되는 마이그레이션이 최종 스키마다. */
    private List<String> latestNotificationTypeEnumValues() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            Path latest = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(this::definesNotificationTypeEnum)
                    // 파일명 사전순은 V8이 V20260813보다 뒤로 가므로 버전을 수로 비교한다.
                    .max(Comparator.comparing(this::version))
                    .orElseThrow(() -> new AssertionError(
                            "notifications.type ENUM을 정의하는 마이그레이션을 찾지 못했습니다."));

            Matcher matcher = ENUM_DEFINITION.matcher(read(latest));
            assertThat(matcher.find()).isTrue();
            return Arrays.stream(matcher.group(1).split(","))
                    .map(value -> value.trim().replace("'", ""))
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }

    private boolean definesNotificationTypeEnum(Path path) {
        return ENUM_DEFINITION.matcher(read(path)).find();
    }

    private BigInteger version(Path path) {
        Matcher matcher = VERSION.matcher(path.getFileName().toString());
        return matcher.find() ? new BigInteger(matcher.group(1)) : BigInteger.ZERO;
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션 파일을 읽지 못했습니다: " + path, e);
        }
    }
}
