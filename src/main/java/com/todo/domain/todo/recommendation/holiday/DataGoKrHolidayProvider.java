package com.todo.domain.todo.recommendation.holiday;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 공공데이터포털을 <b>연-월 단위</b>로 조회하고 메모리에 캐시한다.
 *
 * <p>공휴일은 연 단위로 고시되고 임시공휴일만 드물게 추가되므로 하루 캐시면 충분하다.
 * 개발 계정 한도(10,000회/일)를 고려하면 캐시 없이도 넉넉하지만, 추천 호출마다 외부 API를
 * 타면 그쪽 지연과 장애가 그대로 추천 지연이 된다.
 *
 * <p>실패한 달은 짧게(5분) <b>음성 캐시</b>한다. 장애 중에 추천이 들어올 때마다 5초 타임아웃을
 * 다시 기다리지 않기 위해서다. 키가 없으면 호출 자체를 하지 않는다.
 *
 * <p>단일 인스턴스 전제의 in-memory 캐시다. 인스턴스를 늘리면 각자 따로 채운다 — 그래도
 * 정합성 문제는 없다(같은 원본을 읽는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataGoKrHolidayProvider implements HolidayProvider {

    static final Duration SUCCESS_TTL = Duration.ofHours(24);
    static final Duration FAILURE_TTL = Duration.ofMinutes(5);

    private final HolidayApiClient apiClient;
    private final HolidayApiProperties properties;
    private final Clock clock;
    private final Map<YearMonth, CachedMonth> cache = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable() {
        return properties.hasServiceKey();
    }

    @Override
    public List<Holiday> holidaysBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return List.of();
        }
        if (!isAvailable()) {
            return List.of();
        }

        List<Holiday> result = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            for (Holiday holiday : monthHolidays(month)) {
                if (!holiday.date().isBefore(from) && !holiday.date().isAfter(to)) {
                    result.add(holiday);
                }
            }
        }
        return List.copyOf(result);
    }

    private List<Holiday> monthHolidays(YearMonth month) {
        Instant now = clock.instant();
        CachedMonth cached = cache.get(month);
        if (cached != null && !cached.isExpired(now)) {
            return cached.holidays();
        }

        try {
            List<Holiday> holidays = apiClient.fetchHolidays(month);
            cache.put(month, CachedMonth.success(holidays, now));
            return holidays;
        } catch (HolidayApiException e) {
            // 추천을 막지 않는다. 호출부는 빈 목록을 받고, 원인은 여기서만 남긴다.
            log.warn("공휴일 조회 실패. month={}, reason={}", month, e.getMessage());
            cache.put(month, CachedMonth.failure(now));
            return List.of();
        }
    }

    /** 테스트·운영 점검용. 캐시를 비우면 다음 조회에서 다시 가져온다. */
    void evictAll() {
        cache.clear();
    }

    private record CachedMonth(List<Holiday> holidays, Instant expiresAt) {

        static CachedMonth success(List<Holiday> holidays, Instant now) {
            return new CachedMonth(holidays, now.plus(SUCCESS_TTL));
        }

        static CachedMonth failure(Instant now) {
            return new CachedMonth(List.of(), now.plus(FAILURE_TTL));
        }

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
