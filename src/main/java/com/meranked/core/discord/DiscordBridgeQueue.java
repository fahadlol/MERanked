package com.meranked.core.discord;

import com.meranked.core.logs.MerankedLogEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DiscordBridgeQueue {

    private final int maxSize;
    private final Deque<MerankedLogEvent> queue = new ArrayDeque<>();

    public DiscordBridgeQueue(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    public synchronized void enqueue(MerankedLogEvent event) {
        while (queue.size() >= maxSize) {
            queue.pollFirst();
        }
        queue.offerLast(event);
    }

    public synchronized MerankedLogEvent poll() {
        return queue.pollFirst();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
