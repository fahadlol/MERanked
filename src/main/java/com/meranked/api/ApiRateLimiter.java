package com.meranked.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window rate limiter keyed by client IP. */
final class ApiRateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    ApiRateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMs = Math.max(1000L, windowMs);
    }

    boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> times = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= windowMs) {
                times.pollFirst();
            }
            if (times.size() >= maxRequests) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
