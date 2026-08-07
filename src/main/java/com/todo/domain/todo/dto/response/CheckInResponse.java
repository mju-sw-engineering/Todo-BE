package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.WorkItemCheckIn;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record CheckInResponse(
        @Schema(description = "체크인 ID") Long checkInId,
        @Schema(description = "작성자 ID") Long userId,
        @Schema(description = "작성자 닉네임") String nickname,
        @Schema(description = "체크인 날짜") LocalDate checkDate,
        @Schema(description = "진행 메모") String memo
) {
    public static CheckInResponse from(WorkItemCheckIn checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getUser().getId(),
                checkIn.getUser().getNickname(),
                checkIn.getCheckDate(),
                checkIn.getMemo()
        );
    }
}
