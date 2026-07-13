package com.meranked.core.logs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SuspicionLogService {

    private final MerankedLoggerService logger;
    private final String serverId;

    public SuspicionLogService(MerankedLoggerService logger, String serverId) {
        this.logger = logger;
        this.serverId = serverId;
    }

    public void logSuspicion(String type, LogSeverity severity, String summary, int suspicionScore,
                             String reason, List<String> relatedPlayers, List<String> evidence,
                             String recommendedAction) {
        logger.log(MerankedLogEvent.builder(serverId, LogCategory.SUSPICION, type, severity, summary)
                .put("suspicionScore", suspicionScore)
                .put("reason", reason)
                .put("relatedPlayers", relatedPlayers)
                .put("evidence", evidence)
                .put("recommendedAction", recommendedAction)
                .build());
    }

    public void logRatingSpike(UUID player, String playerName, double change) {
        logSuspicion("RATING_SPIKE", LogSeverity.HIGH,
                "Rating spike for " + playerName + ": +" + (int) change,
                Math.min(100, (int) (change / 2)),
                "Unusual rating gain",
                List.of(playerName),
                List.of("Gain: " + (int) change),
                "Staff review recommended");
    }

    public void logFriendFarming(UUID a, UUID b, int matches, int gamemodes) {
        String n1 = playerName(a);
        String n2 = playerName(b);
        logSuspicion("FRIEND_FARMING_SUSPECTED", LogSeverity.HIGH,
                "Possible friend farming: " + n1 + " fought " + n2 + " " + matches + " times",
                Math.min(100, matches * 10),
                "Repeated matches against same player",
                List.of(n1, n2),
                List.of(matches + " matches in window", gamemodes + " gamemodes"),
                "Staff review recommended");
    }

    private String playerName(UUID uuid) {
        String n = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? uuid.toString().substring(0, 8) : n;
    }
}
