package com.todo.global.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 고아 파일 정리 스케줄러 설정.
 *
 * <p>{@code dryRun}이 안전장치의 핵심이다. 기본값 {@code true}에서는 지웠을 키를 로그로만
 * 남기고 아무것도 삭제하지 않는다. 운영 로그에서 오탐이 없음을 확인한 뒤
 * {@code ORPHAN_CLEANUP_DRY_RUN=false}로 내려 실제 삭제를 켠다.
 *
 * <p>{@code graceHours}는 "업로드 후 제출까지 사용자가 최대 얼마나 뜸 들일 수 있나"의
 * 상한이다. 짧게 잡아 얻는 것은 스토리지 몇 MB뿐이고, 잘못 잡으면 제출하려던 파일을 지워
 * 사용자가 원인 모를 "파일 없음" 오류를 맞는다. 비용이 비대칭이므로 길게 둔다.
 */
@ConfigurationProperties(prefix = "orphan-cleanup")
public record OrphanCleanupProperties(
        Boolean enabled,
        Boolean dryRun,
        Integer graceHours,
        Integer batchSize
) {
    public OrphanCleanupProperties {
        enabled = enabled == null || enabled;
        dryRun = dryRun == null || dryRun;
        graceHours = graceHours == null ? 24 : graceHours;
        batchSize = batchSize == null ? 500 : batchSize;
    }
}
