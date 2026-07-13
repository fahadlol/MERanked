package com.meranked.core.logs;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchLogService {

    private final MerankedLoggerService logger;
    private final ServiceRegistry services;

    public MatchLogService(ServiceRegistry services, MerankedLoggerService logger) {
        this.services = services;
        this.logger = logger;
    }

    public void logMatchStart(RankedMatch match, String arena) {
        Map<String, Object> data = matchData(match, arena);
        logger.log(LogCategory.MATCH, "MATCH_START", LogSeverity.INFO,
                "Match " + match.matchId() + " started");
        logger.log(MerankedLogEvent.builder(serverId(), LogCategory.MATCH, "MATCH_START", LogSeverity.INFO,
                "Match started: " + name(match.player1()) + " vs " + name(match.player2()))
                .data(data).build());
    }

    public void logMatchEnd(RankedMatch match, UUID winner, UUID loser, String score, long durationMs,
                            Map<UUID, Double> ratingChange) {
        Map<String, Object> data = matchData(match, match.arenaName());
        data.put("winner", name(winner));
        data.put("loser", name(loser));
        data.put("score", score);
        data.put("durationMs", durationMs);
        data.put("ratingChange", ratingChange);
        logger.log(MerankedLogEvent.builder(serverId(), LogCategory.MATCH, "MATCH_END", LogSeverity.INFO,
                name(winner) + " beat " + name(loser) + " (" + score + ")")
                .player(name(winner), winner)
                .data(data).build());
    }

    public void logMatchCancel(String matchId, String reason) {
        logger.log(LogCategory.MATCH, "MATCH_CANCELLED", LogSeverity.MEDIUM,
                "Match " + matchId + " cancelled: " + reason);
    }

    public void logMatchDisconnect(String matchId, UUID player, String reason) {
        logger.logPlayer(LogCategory.MATCH, "MATCH_DISCONNECT", LogSeverity.MEDIUM,
                name(player) + " disconnected during match " + matchId + ": " + reason,
                name(player), player);
    }

    private Map<String, Object> matchData(RankedMatch match, String arena) {
        Map<String, Object> data = new HashMap<>();
        data.put("matchId", match.matchId());
        data.put("gamemode", match.gamemode());
        data.put("arena", arena);
        data.put("player1", name(match.player1()));
        data.put("player2", name(match.player2()));
        data.put("ratingBefore", match.ratingBefore());
        return data;
    }

    private String name(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? "Unknown" : n;
    }

    private String serverId() {
        return services.config().get("config.yml").getString("discord-bridge.server-id", "meranked-main");
    }
}
