package com.todo.global.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스 전제의 in-memory 슬라이딩 윈도우 rate limiter.
 * 외부 저장소 없이 동작하므로 인스턴스를 늘리면 인스턴스별로 한도가 적용된다.
 */
@Component
@RequiredArgsConstructor
public class SimpleRateLimiter {

    private static final Duration ENTRY_RETENTION = Duration.ofHours(1);

    private final Clock clock;
    private final Map<String, Deque<Long>> hitsByKey = new ConcurrentHashMap<>();

    /**
     * 키별로 window 안에서 limit번까지 허용한다. 허용되면 true, 한도를 넘으면 false.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        Deque<Long> hits = hitsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (hits) {
            evictBefore(hits, windowStart);
            if (hits.size() >= limit) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    @Scheduled(fixedDelayString = "${ratelimit.cleanup-interval-ms:600000}")
    public void evictStaleKeys() {
        long threshold = clock.millis() - ENTRY_RETENTION.toMillis();
        hitsByKey.forEach((key, hits) -> {
            synchronized (hits) {
                evictBefore(hits, threshold);
                if (hits.isEmpty()) {
                    hitsByKey.remove(key, hits);
                }
            }
        });
    }

    private void evictBefore(Deque<Long> hits, long threshold) {
        while (!hits.isEmpty() && hits.peekFirst() < threshold) {
            hits.pollFirst();
        }
    }
}
