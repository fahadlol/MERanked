package com.meranked.model;

import java.util.UUID;

public record RankedPlayer(
        UUID uuid,
        String username,
        String region,
        boolean regionHidden,
        int suspicionScore,
        long createdAt,
        long lastSeen
) {
    public static RankedPlayer create(UUID uuid, String username) {
        long now = System.currentTimeMillis();
        return new RankedPlayer(uuid, username, "Other", false, 0, now, now);
    }
}
