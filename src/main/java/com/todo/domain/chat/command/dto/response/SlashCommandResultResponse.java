package com.todo.domain.chat.command.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "슬래시 명령어 실행 결과 조회 응답")
public record SlashCommandResultResponse(
        @Schema(description = "이 결과를 촉발한 채팅 메시지 ID") Long messageId,
        @Schema(description = "명령어", example = "MY_TODOS") String command,
        @Schema(description = "실행 상태", example = "DONE") String status,
        @Schema(description = "실행 결과. 처리 중(PENDING)이면 null") JsonNode result,
        @Schema(description = "실행 시각. 처리 중(PENDING)이면 null") OffsetDateTime executedAt
) {
    public static SlashCommandResultResponse from(SlashCommandExecution execution, ObjectMapper objectMapper) {
        return new SlashCommandResultResponse(
                execution.getChatMessage().getId(),
                execution.getCommand().name(),
                execution.getStatus().name(),
                parseResult(execution.getResultJson(), objectMapper),
                execution.getExecutedAt() == null ? null : execution.getExecutedAt().atOffset(ZoneOffset.ofHours(9))
        );
    }

    private static JsonNode parseResult(String resultJson, ObjectMapper objectMapper) {
        if (resultJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(resultJson);
        } catch (JsonProcessingException e) {
            throw new BusinessException("명령어 실행 결과를 읽는 데 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
