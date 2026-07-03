package com.meranked.settings;

import java.util.UUID;

public record PlayerSettings(
        UUID uuid,
        boolean messagesEnabled,
        boolean spectateRequestsEnabled,
        boolean queueNotifications,
        boolean regionHidden
) {
    public PlayerSettings withMessages(boolean v) {
        return new PlayerSettings(uuid, v, spectateRequestsEnabled, queueNotifications, regionHidden);
    }
    public PlayerSettings withSpectateRequests(boolean v) {
        return new PlayerSettings(uuid, messagesEnabled, v, queueNotifications, regionHidden);
    }
    public PlayerSettings withQueueNotifications(boolean v) {
        return new PlayerSettings(uuid, messagesEnabled, spectateRequestsEnabled, v, regionHidden);
    }
    public PlayerSettings withRegionHidden(boolean v) {
        return new PlayerSettings(uuid, messagesEnabled, spectateRequestsEnabled, queueNotifications, v);
    }
}
