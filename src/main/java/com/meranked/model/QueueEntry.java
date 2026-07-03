package com.meranked.model;

import java.util.UUID;

public record QueueEntry(
        UUID uuid,
        String gamemode,
        double rating,
        double ratingDeviation,
        String confidence,
        long joinedAt,
        String ip
) {
    public long queueTimeSeconds() {
        return (System.currentTimeMillis() - joinedAt) / 1000;
    }
}
