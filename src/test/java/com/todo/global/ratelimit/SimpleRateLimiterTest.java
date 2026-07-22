package com.todo.global.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    void 한도_안에서는_허용하고_한도를_넘으면_차단한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        SimpleRateLimiter rateLimiter = new SimpleRateLimiter(clock);

        assertThat(rateLimiter.tryAcquire("key", 2, WINDOW)).isTrue();
        assertThat(rateLimiter.tryAcquire("key", 2, WINDOW)).isTrue();
        assertThat(rateLimiter.tryAcquire("key", 2, WINDOW)).isFalse();
    }

    @Test
    void 윈도우가_지나면_다시_허용한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        SimpleRateLimiter rateLimiter = new SimpleRateLimiter(clock);
        rateLimiter.tryAcquire("key", 1, WINDOW);

        assertThat(rateLimiter.tryAcquire("key", 1, WINDOW)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(rateLimiter.tryAcquire("key", 1, WINDOW)).isTrue();
    }

    @Test
    void 키마다_한도를_따로_계산한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        SimpleRateLimiter rateLimiter = new SimpleRateLimiter(clock);
        rateLimiter.tryAcquire("a", 1, WINDOW);

        assertThat(rateLimiter.tryAcquire("a", 1, WINDOW)).isFalse();
        assertThat(rateLimiter.tryAcquire("b", 1, WINDOW)).isTrue();
    }

    @Test
    void 오래된_키는_정리되고_정리_후에도_한도가_동작한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        SimpleRateLimiter rateLimiter = new SimpleRateLimiter(clock);
        rateLimiter.tryAcquire("key", 1, WINDOW);

        clock.advance(Duration.ofHours(2));
        rateLimiter.evictStaleKeys();

        assertThat(rateLimiter.tryAcquire("key", 1, WINDOW)).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
