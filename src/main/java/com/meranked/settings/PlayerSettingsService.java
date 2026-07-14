package com.meranked.settings;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerSettings>> settingsLoads = new ConcurrentHashMap<>();

    public PlayerSettingsService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        ensureTable();
    }

    private void ensureTable() {
        database.executeAsync("ensureSettingsTable", conn -> {
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

    public PlayerSettings getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerSettings get(UUID uuid) {
        PlayerSettings cached = getCached(uuid);
        if (cached != null) return cached;
        loadAsync(uuid);
        return defaults(uuid);
    }

    public CompletableFuture<PlayerSettings> getAsync(UUID uuid) {
        PlayerSettings cached = getCached(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return loadAsync(uuid);
    }

    public CompletableFuture<PlayerSettings> loadAsync(UUID uuid) {
        PlayerSettings cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<PlayerSettings> existing = settingsLoads.get(uuid);
        if (existing != null) return existing;

        CompletableFuture<PlayerSettings> created = new CompletableFuture<>();
        CompletableFuture<PlayerSettings> prior = settingsLoads.putIfAbsent(uuid, created);
        if (prior != null) return prior;

        database.queryAsync("loadPlayerSettings", conn -> {
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
            return defaults(uuid);
        }).whenComplete((settings, error) -> {
            settingsLoads.remove(uuid);
            if (error != null) {
                plugin.getLogger().warning("Failed to load settings for " + uuid + ": " + error.getMessage());
                created.complete(defaults(uuid));
                return;
            }
            cache.put(uuid, settings);
            created.complete(settings);
        });
        return created;
    }

    public void save(PlayerSettings settings) {
        cache.put(settings.uuid(), settings);
        database.executeAsync("savePlayerSettings", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_player_settings
                (uuid, messages_enabled, spectate_requests, queue_notifications, region_hidden)
                VALUES (?,?,?,?,?)
                """)) {
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

    private PlayerSettings defaults(UUID uuid) {
        FileConfiguration defaults = configService.get("settings.yml");
        return new PlayerSettings(uuid,
                defaults.getBoolean("defaults.messages-enabled", true),
                defaults.getBoolean("defaults.spectate-requests-enabled", true),
                defaults.getBoolean("defaults.queue-notifications", true),
                defaults.getBoolean("defaults.region-hidden", false));
    }
}
