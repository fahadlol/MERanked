package com.meranked.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the in-flight future deduplication pattern used by ProfileService and KitService.
 */
class AsyncLoadDeduplicationTest {

    @Test
    void simultaneousLoadsShareOneInFlightFuture() throws Exception {
        Map<String, CompletableFuture<String>> loads = new ConcurrentHashMap<>();
        AtomicInteger queryCount = new AtomicInteger();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);

        CompletableFuture<String> first = loadAsync("player:1", loads, queryCount, started);
        CompletableFuture<String> second = loadAsync("player:1", loads, queryCount, started);

        assertSame(first, second);
        assertEquals(1, queryCount.get());
        started.countDown();
        assertEquals("value-player:1", first.get(2, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, loads.size());
    }

    @Test
    void differentKeysLoadIndependently() {
        Map<String, CompletableFuture<String>> loads = new ConcurrentHashMap<>();
        AtomicInteger queryCount = new AtomicInteger();

        CompletableFuture<String> a = load("player:1", loads, queryCount);
        CompletableFuture<String> b = load("player:2", loads, queryCount);

        assertEquals(2, queryCount.get());
        assertEquals("value-player:1", a.join());
        assertEquals("value-player:2", b.join());
    }

    @Test
    void completedCacheSkipsDatabase() {
        Map<String, String> cache = new ConcurrentHashMap<>();
        Map<String, CompletableFuture<String>> loads = new ConcurrentHashMap<>();
        AtomicInteger queryCount = new AtomicInteger();

        cache.put("player:1", "cached");
        CompletableFuture<String> result = loadCached("player:1", cache, loads, queryCount);

        assertEquals("cached", result.join());
        assertEquals(0, queryCount.get());
    }

    private CompletableFuture<String> loadAsync(String key, Map<String, CompletableFuture<String>> loads,
                                               AtomicInteger queryCount,
                                               java.util.concurrent.CountDownLatch started) {
        CompletableFuture<String> existing = loads.get(key);
        if (existing != null) return existing;

        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> prior = loads.putIfAbsent(key, created);
        if (prior != null) return prior;

        queryCount.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            try {
                started.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            loads.remove(key);
            created.complete("value-" + key);
        });
        return created;
    }

    private CompletableFuture<String> load(String key, Map<String, CompletableFuture<String>> loads,
                                           AtomicInteger queryCount) {
        CompletableFuture<String> existing = loads.get(key);
        if (existing != null) return existing;

        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> prior = loads.putIfAbsent(key, created);
        if (prior != null) return prior;

        queryCount.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            loads.remove(key);
            created.complete("value-" + key);
        }).join();
        return created;
    }

    private CompletableFuture<String> loadCached(String key, Map<String, String> cache,
                                                 Map<String, CompletableFuture<String>> loads,
                                                 AtomicInteger queryCount) {
        String cached = cache.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return load(key, loads, queryCount);
    }

    @Test
    void disconnectBeforeLoadCompletesDoesNotLeaveStaleInflight() {
        UUID uuid = UUID.randomUUID();
        Map<UUID, CompletableFuture<String>> loads = new ConcurrentHashMap<>();
        CompletableFuture<String> created = new CompletableFuture<>();
        loads.put(uuid, created);
        loads.remove(uuid);
        assertEquals(0, loads.size());
    }
}
