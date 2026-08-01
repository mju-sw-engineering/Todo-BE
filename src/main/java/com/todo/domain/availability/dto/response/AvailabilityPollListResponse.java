package com.todo.domain.availability.dto.response;

import com.todo.domain.availability.entity.AvailabilityPoll;
import com.todo.domain.availability.entity.AvailabilityPollDate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record AvailabilityPollListResponse(
        @Schema(description = "투표 ID") Long id,
        @Schema(description = "제목") String title,
        @Schema(description = "가장 이른 날짜") LocalDate dateFrom,
        @Schema(description = "가장 늦은 날짜") LocalDate dateTo,
        @Schema(description = "전체 팀원 수") long totalMemberCount,
        @Schema(description = "응답한 팀원 수") long respondedCount,
        @Schema(description = "내가 응답했는지 여부") boolean myResponded,
        @Schema(description = "전원 응답 여부") boolean allResponded
) {
    public static AvailabilityPollListResponse of(
            AvailabilityPoll poll,
            long totalMemberCount,
            long respondedCount,
            boolean myResponded
    ) {
        List<LocalDate> dates = poll.getDates().stream()
                .map(AvailabilityPollDate::getDate)
                .sorted()
                .toList();
        LocalDate dateFrom = dates.isEmpty() ? null : dates.get(0);
        LocalDate dateTo = dates.isEmpty() ? null : dates.get(dates.size() - 1);

        return new AvailabilityPollListResponse(
                poll.getId(),
                poll.getTitle(),
                dateFrom,
                dateTo,
                totalMemberCount,
                respondedCount,
                myResponded,
                totalMemberCount > 0 && respondedCount >= totalMemberCount
        );
    }
}
