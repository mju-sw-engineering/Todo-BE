package com.todo.domain.todo.recommendation.holiday;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DataGoKrHolidayProviderTest {

    private static final Holiday GAECHEONJEOL = new Holiday(LocalDate.of(2026, 10, 3), "개천절");
    private static final Holiday HANGEUL = new Holiday(LocalDate.of(2026, 10, 9), "한글날");
    private static final Holiday CHUSEOK = new Holiday(LocalDate.of(2026, 9, 25), "추석");

    @Mock
    private HolidayApiClient apiClient;

    private MutableClock clock;
    private DataGoKrHolidayProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-20T00:00:00Z"));
        provider = new DataGoKrHolidayProvider(apiClient, properties("key"), clock);
    }

    @Test
    void 구간이_월_경계를_넘으면_두_달을_조회하고_구간_안의_공휴일만_돌려준다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 9))).willReturn(List.of(CHUSEOK));
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10))).willReturn(List.of(GAECHEONJEOL, HANGEUL));

        List<Holiday> holidays = provider.holidaysBetween(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 5));

        assertThat(holidays).containsExactly(GAECHEONJEOL);
    }

    @Test
    void 같은_달을_다시_조회하면_캐시를_쓰고_API를_부르지_않는다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10))).willReturn(List.of(GAECHEONJEOL, HANGEUL));

        provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        clock.advance(Duration.ofHours(23));
        List<Holiday> second = provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(second).containsExactly(GAECHEONJEOL, HANGEUL);
        then(apiClient).should(times(1)).fetchHolidays(YearMonth.of(2026, 10));
    }

    @Test
    void 성공_캐시는_24시간이_지나면_다시_조회한다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10))).willReturn(List.of(GAECHEONJEOL));

        provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        clock.advance(Duration.ofHours(24));
        provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        then(apiClient).should(times(2)).fetchHolidays(YearMonth.of(2026, 10));
    }

    @Test
    void 조회가_실패하면_빈_목록을_돌려주고_5분간_다시_부르지_않는다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10))).willThrow(new HolidayApiException("타임아웃"));

        List<Holiday> first = provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        clock.advance(Duration.ofMinutes(4));
        List<Holiday> second = provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        then(apiClient).should(times(1)).fetchHolidays(YearMonth.of(2026, 10));
    }

    @Test
    void 실패_캐시는_5분_뒤_다시_시도하고_성공하면_결과를_돌려준다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10)))
                .willThrow(new HolidayApiException("타임아웃"))
                .willReturn(List.of(GAECHEONJEOL));

        provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        clock.advance(Duration.ofMinutes(5));
        List<Holiday> recovered = provider.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        assertThat(recovered).containsExactly(GAECHEONJEOL);
        then(apiClient).should(times(2)).fetchHolidays(YearMonth.of(2026, 10));
    }

    @Test
    void 한_달이_실패해도_다른_달의_결과는_돌려준다() {
        given(apiClient.fetchHolidays(YearMonth.of(2026, 9))).willThrow(new HolidayApiException("503"));
        given(apiClient.fetchHolidays(YearMonth.of(2026, 10))).willReturn(List.of(GAECHEONJEOL));

        List<Holiday> holidays = provider.holidaysBetween(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 5));

        assertThat(holidays).containsExactly(GAECHEONJEOL);
    }

    @Test
    void 서비스_키가_없으면_API를_부르지_않고_사용_불가로_표시한다() {
        DataGoKrHolidayProvider withoutKey = new DataGoKrHolidayProvider(apiClient, properties(""), clock);

        List<Holiday> holidays = withoutKey.holidaysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        assertThat(holidays).isEmpty();
        assertThat(withoutKey.isAvailable()).isFalse();
        then(apiClient).should(never()).fetchHolidays(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 시작일이_종료일보다_뒤면_빈_목록이다() {
        assertThat(provider.holidaysBetween(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1))).isEmpty();
        then(apiClient).should(never()).fetchHolidays(org.mockito.ArgumentMatchers.any());
    }

    private HolidayApiProperties properties(String serviceKey) {
        return new HolidayApiProperties(serviceKey, "https://apis.example.test", null, null);
    }

    /** 테스트에서 시간을 앞으로 돌리기 위한 시계. */
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
