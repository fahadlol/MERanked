package com.meranked.core.logs;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.core.discord.BridgeTextUtil;
import com.meranked.core.discord.DiscordBridgeManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MerankedLoggerService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private DiscordBridgeManager bridge;

    public MerankedLoggerService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void bindBridge(DiscordBridgeManager bridge) {
        this.bridge = bridge;
    }

    public void log(MerankedLogEvent event) {
        if (!isCategoryEnabled(event.category())) return;
        if (bridge == null || !bridge.config().enabled()) return;
        bridge.enqueue(sanitize(event));
    }

    public void log(LogCategory category, String type, LogSeverity severity, String summary) {
        log(MerankedLogEvent.builder(serverId(), category, type, severity, BridgeTextUtil.sanitize(summary)).build());
    }

    public void logPlayer(LogCategory category, String type, LogSeverity severity, String summary,
                          String playerName, UUID playerUuid) {
        log(MerankedLogEvent.builder(serverId(), category, type, severity, BridgeTextUtil.sanitize(summary))
                .player(playerName, playerUuid)
                .build());
    }

    public void logStaff(LogCategory category, String type, LogSeverity severity, String summary,
                         String staffName, UUID staffUuid, Map<String, Object> data) {
        var builder = MerankedLogEvent.builder(serverId(), category, type, severity, BridgeTextUtil.sanitize(summary))
                .staff(staffName, staffUuid);
        if (data != null) builder.data(data);
        log(builder.build());
    }

    public void logStaffPlayer(LogCategory category, String type, LogSeverity severity, String summary,
                               String staffName, UUID staffUuid, String playerName, UUID playerUuid,
                               Map<String, Object> data) {
        var builder = MerankedLogEvent.builder(serverId(), category, type, severity, BridgeTextUtil.sanitize(summary))
                .staff(staffName, staffUuid)
                .player(playerName, playerUuid);
        if (data != null) builder.data(data);
        log(builder.build());
    }

    private boolean isCategoryEnabled(LogCategory category) {
        FileConfiguration cfg = configService.get("config.yml");
        return cfg.getBoolean("logs." + category.configKey(), true);
    }

    private String serverId() {
        return configService.get("config.yml").getString("discord-bridge.server-id", "meranked-main");
    }

    private MerankedLogEvent sanitize(MerankedLogEvent event) {
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, Object> entry : event.data().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                data.put(entry.getKey(), BridgeTextUtil.sanitize(s));
            } else {
                data.put(entry.getKey(), value);
            }
        }
        var builder = MerankedLogEvent.builder(event.serverId(), event.category(), event.type(),
                        event.severity(), BridgeTextUtil.sanitize(event.summary()))
                .data(data);
        if (event.player() != null) {
            String uuid = (String) event.player().get("uuid");
            builder.player((String) event.player().get("name"),
                    uuid == null || uuid.isEmpty() ? null : UUID.fromString(uuid));
        }
        if (event.staff() != null) {
            String uuid = (String) event.staff().get("uuid");
            builder.staff((String) event.staff().get("name"),
                    uuid == null || uuid.isEmpty() ? null : UUID.fromString(uuid));
        }
        return builder.build();
    }
}
