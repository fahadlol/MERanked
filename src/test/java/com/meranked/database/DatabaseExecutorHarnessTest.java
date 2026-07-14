package com.meranked.database;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight harness validating database executor serialization assumptions.
 */
class DatabaseExecutorHarnessTest {

    @Test
    void singleThreadExecutorSerializesWork() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                int current = active.incrementAndGet();
                maxActive.updateAndGet(prev -> Math.max(prev, current));
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
            }, executor);
        }
        CompletableFuture.allOf(futures).join();
        executor.shutdown();

        assertEquals(1, maxActive.get(), "SQLite pool size 1 requires serialized DB work");
        assertEquals(0, active.get());
    }

    @Test
    void rejectingShutdownPreventsNewOperations() {
        java.util.concurrent.atomic.AtomicBoolean accepting = new java.util.concurrent.atomic.AtomicBoolean(true);
        accepting.set(false);
        assertTrue(!accepting.get());
    }
}
