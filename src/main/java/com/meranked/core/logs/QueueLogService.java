package com.meranked.core.logs;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.QueueEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QueueLogService {

    private final MerankedLoggerService logger;
    private final ServiceRegistry services;

    public QueueLogService(ServiceRegistry services, MerankedLoggerService logger) {
        this.services = services;
        this.logger = logger;
    }

    public void logQueueJoin(Player player, String gamemode, String region, int rating) {
        Map<String, Object> data = new HashMap<>();
        data.put("gamemode", gamemode);
        data.put("region", region);
        data.put("rating", rating);
        logger.logPlayer(LogCategory.QUEUE, "QUEUE_JOIN", LogSeverity.INFO,
                player.getName() + " joined " + gamemode + " queue", player.getName(), player.getUniqueId());
        logger.log(MerankedLogEvent.builder(serverId(), LogCategory.QUEUE, "QUEUE_JOIN", LogSeverity.INFO,
                player.getName() + " joined " + gamemode + " queue")
                .player(player.getName(), player.getUniqueId())
                .data(data)
                .build());
    }

    public void logQueueLeave(Player player, String reason) {
        logger.logPlayer(LogCategory.QUEUE, "QUEUE_LEAVE", LogSeverity.INFO,
                player.getName() + " left queue: " + reason, player.getName(), player.getUniqueId());
    }

    public void logQueueDodge(Player player, String gamemode, String reason) {
        Map<String, Object> data = Map.of("gamemode", gamemode, "reason", reason);
        logger.logPlayer(LogCategory.QUEUE, "QUEUE_DODGE", LogSeverity.MEDIUM,
                player.getName() + " dodge detected: " + reason, player.getName(), player.getUniqueId());
        logger.log(MerankedLogEvent.builder(serverId(), LogCategory.QUEUE, "QUEUE_DODGE", LogSeverity.MEDIUM,
                player.getName() + " dodge detected").player(player.getName(), player.getUniqueId()).data(data).build());
    }

    public void logMatchFound(UUID p1, UUID p2, String gamemode, String region) {
        String n1 = name(p1);
        String n2 = name(p2);
        Map<String, Object> data = new HashMap<>();
        data.put("gamemode", gamemode);
        data.put("region", region);
        data.put("player1", n1);
        data.put("player2", n2);
        logger.log(LogCategory.QUEUE, "MATCH_FOUND", LogSeverity.INFO,
                "Match found: " + n1 + " vs " + n2 + " (" + gamemode + ")");
        logger.log(MerankedLogEvent.builder(serverId(), LogCategory.QUEUE, "MATCH_FOUND", LogSeverity.INFO,
                "Match found: " + n1 + " vs " + n2).data(data).build());
    }

    private String name(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? uuid.toString().substring(0, 8) : n;
    }

    private String serverId() {
        return services.config().get("config.yml").getString("discord-bridge.server-id", "meranked-main");
    }
}
