package com.todo.domain.todo.recommendation;

import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 모델 출력을 등록 가능한 카드로 바꾼다. 모델 출력을 그대로 믿지 않는다 — strict 스키마는
 * <i>모양</i>만 보장하지 <i>값</i>은 보장하지 않는다. 과거 날짜, 다른 팀 투두 id, 팀원이 아닌
 * 담당자 id가 올 수 있다.
 */
@Slf4j
@Component
public class RecommendationResultSanitizer {

    static final int MAX_ITEMS = 3;
    static final int TITLE_MAX = 100;
    static final int DESCRIPTION_MAX = 255;
    static final int REASON_MAX = 255;
    /** 제안 마감의 시각. 날짜만 받아 서버가 고정한다 — 모델에게 시각까지 고르게 할 이유가 없다. */
    static final LocalTime DEADLINE_TIME = LocalTime.of(21, 0);
    static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public List<TeamTodoRecommendationItem> sanitize(AiRecommendationResponse response, TeamActivityDigest digest) {
        if (response == null || response.recommendations() == null) {
            return List.of();
        }
        List<TeamTodoRecommendationItem> items = new ArrayList<>();
        for (AiRecommendationResponse.Item raw : response.recommendations()) {
            if (items.size() >= MAX_ITEMS) {
                log.debug("추천이 {}개를 넘어 나머지를 버립니다.", MAX_ITEMS);
                break;
            }
            if (raw == null) {
                continue;
            }
            String title = clip(raw.title(), TITLE_MAX);
            if (title.isEmpty()) {
                // 제목 없는 추천은 등록할 수 없다. 카드에도 올리지 않는다.
                continue;
            }
            items.add(TeamTodoRecommendationItem.of(
                    items.size(),
                    RecommendationKind.fromModelValue(raw.kind()),
                    title,
                    clip(raw.description(), DESCRIPTION_MAX),
                    clip(raw.reason(), REASON_MAX),
                    resolveDeadline(raw.suggested_deadline(), digest),
                    digest.hasTodo(raw.related_todo_id()) ? raw.related_todo_id() : null,
                    memberOnly(raw.suggested_assignee_ids(), digest)
            ));
        }
        return List.copyOf(items);
    }

    /**
     * 오늘 이전이거나 읽을 수 없는 날짜는 다음 평일(주말·공휴일 제외)로 보정한다.
     * 모델이 <b>고른</b> 공휴일은 손대지 않는다 — 연휴 중에 할 일일 수 있고, 프롬프트도
     * 공휴일 회피를 선호이지 금지로 두지 않았다.
     */
    OffsetDateTime resolveDeadline(String raw, TeamActivityDigest digest) {
        LocalDate date = parse(raw);
        if (date == null || date.isBefore(digest.today())) {
            date = nextWorkingDay(digest.today(), digest);
        }
        return date.atTime(DEADLINE_TIME).atOffset(KST);
    }

    private LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate nextWorkingDay(LocalDate from, TeamActivityDigest digest) {
        LocalDate date = from.plusDays(1);
        while (isWeekend(date) || digest.isHoliday(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private List<Long> memberOnly(List<Long> ids, TeamActivityDigest digest) {
        if (ids == null) {
            return List.of();
        }
        Set<Long> members = new LinkedHashSet<>();
        for (Long id : ids) {
            if (digest.isMember(id)) {
                members.add(id);
            }
        }
        return List.copyOf(members);
    }

    private static String clip(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String text = raw.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
