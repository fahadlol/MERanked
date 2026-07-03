package com.meranked.model;

import java.util.UUID;

public record StaffAlert(
        String alertId,
        String type,
        AlertSeverity severity,
        String reason,
        String matchId,
        String gamemode,
        String arena,
        String players,
        long createdAt,
        boolean resolved,
        UUID flaggedBy
) {}
