package com.meranked.lobby;

import java.util.Locale;
import java.util.Optional;

public enum LobbyItemType {
    QUEUE,
    PROFILE,
    KIT_EDITOR,
    SETTINGS,
    LEADERBOARDS;

    public static Optional<LobbyItemType> fromAction(String action) {
        if (action == null || action.isBlank()) return Optional.empty();
        String normalized = action.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (LobbyItemType type : values()) {
            if (type.configKey().equals(normalized)) return Optional.of(type);
        }
        return Optional.empty();
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
