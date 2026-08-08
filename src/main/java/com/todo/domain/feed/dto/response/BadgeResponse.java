package com.todo.domain.feed.dto.response;

import com.todo.domain.feed.service.BadgeType;
import io.swagger.v3.oas.annotations.media.Schema;

public record BadgeResponse(
        @Schema(description = "배지 ID", example = "first-honey")
        String id,

        @Schema(description = "배지 이름", example = "첫 꿀")
        String label,

        @Schema(description = "아이콘 종류 (drop / bee / hive)", example = "drop")
        String icon,

        @Schema(description = "획득 여부", example = "true")
        boolean acquired
) {
    public static BadgeResponse of(BadgeType type, boolean acquired) {
        return new BadgeResponse(type.getId(), type.getLabel(), type.getIcon(), acquired);
    }
}
