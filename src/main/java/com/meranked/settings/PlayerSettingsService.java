package com.meranked.settings;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public PlayerSettingsService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        ensureTable();
    }

    private void ensureTable() {
        database.executeAsync(conn -> {
            try (var st = conn.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_player_settings (
                        uuid VARCHAR(36) PRIMARY KEY,
                        messages_enabled BOOLEAN DEFAULT TRUE,
                        spectate_requests BOOLEAN DEFAULT TRUE,
                        queue_notifications BOOLEAN DEFAULT TRUE,
                        region_hidden BOOLEAN DEFAULT FALSE
                    )
                    """);
            }
        });
    }

    public PlayerSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    public void save(PlayerSettings settings) {
        cache.put(settings.uuid(), settings);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                INSERT OR REPLACE INTO ranked_player_settings
                (uuid, messages_enabled, spectate_requests, queue_notifications, region_hidden)
                VALUES (?,?,?,?,?)
                """))) {
                ps.setString(1, settings.uuid().toString());
                ps.setBoolean(2, settings.messagesEnabled());
                ps.setBoolean(3, settings.spectateRequestsEnabled());
                ps.setBoolean(4, settings.queueNotifications());
                ps.setBoolean(5, settings.regionHidden());
                ps.executeUpdate();
            }
        });
    }

    public void toggle(UUID uuid, String option) {
        PlayerSettings s = get(uuid);
        PlayerSettings updated = switch (option) {
            case "messages" -> s.withMessages(!s.messagesEnabled());
            case "spectate-requests" -> s.withSpectateRequests(!s.spectateRequestsEnabled());
            case "queue-notifications" -> s.withQueueNotifications(!s.queueNotifications());
            case "region-display" -> s.withRegionHidden(!s.regionHidden());
            default -> s;
        };
        save(updated);
    }

    private PlayerSettings load(UUID uuid) {
        FileConfiguration defaults = configService.get("settings.yml");
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_player_settings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerSettings(uuid,
                                rs.getBoolean("messages_enabled"),
                                rs.getBoolean("spectate_requests"),
                                rs.getBoolean("queue_notifications"),
                                rs.getBoolean("region_hidden"));
                    }
                }
            }
            return new PlayerSettings(uuid,
                    defaults.getBoolean("defaults.messages-enabled", true),
                    defaults.getBoolean("defaults.spectate-requests-enabled", true),
                    defaults.getBoolean("defaults.queue-notifications", true),
                    defaults.getBoolean("defaults.region-hidden", false));
        }).join();
    }
}
