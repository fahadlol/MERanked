package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SuspicionService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.List<GainEntry>> hourlyGains = new ConcurrentHashMap<>();

    public SuspicionService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
    }

    public void startDecayTask() {
        FileConfiguration config = configService.get("suspicion.yml");
        if (!config.getBoolean("decay-enabled", true)) return;
        plugin.tasks().runAsyncTimer(this::decayAll, 20L * 60 * 60, 20L * 60 * 60);
    }

    public int getScore(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadScore);
    }

    public void addScore(UUID uuid, int amount, String reason) {
        FileConfiguration config = configService.get("suspicion.yml");
        if (!config.getBoolean("enabled", true)) return;
        int maxScore = config.getInt("max-score", 100);
        int newScore = Math.min(maxScore, getScore(uuid) + amount);
        cache.put(uuid, newScore);
        saveScore(uuid, newScore);
        if (reason != null && !reason.isBlank()) {
            plugin.getLogger().info("[Suspicion] " + uuid + " +" + amount + " (" + reason + ") -> " + newScore);
        }
    }

    public void lowerScore(UUID uuid, int amount) {
        int newScore = Math.max(0, getScore(uuid) - amount);
        cache.put(uuid, newScore);
        saveScore(uuid, newScore);
    }

    public void clearScore(UUID uuid) {
        cache.put(uuid, 0);
        saveScore(uuid, 0);
    }

    public void checkRatingSpike(UUID uuid, double change) {
        FileConfiguration config = configService.get("config.yml");
        if (!config.getBoolean("rating-spike-detection.enabled", true)) return;

        long now = System.currentTimeMillis();
        hourlyGains.computeIfAbsent(uuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .removeIf(e -> now - e.timestamp > 86400000L);
        hourlyGains.get(uuid).add(new GainEntry(now, change));

        double hourGain = hourlyGains.get(uuid).stream()
                .filter(e -> now - e.timestamp <= 3600000L)
                .mapToDouble(GainEntry::amount).sum();
        double dayGain = hourlyGains.get(uuid).stream().mapToDouble(GainEntry::amount).sum();

        if (hourGain >= config.getInt("rating-spike-detection.max-rating-gain-per-hour", 180)
                || dayGain >= config.getInt("rating-spike-detection.max-rating-gain-per-day", 350)) {
            addScore(uuid, configService.get("suspicion.yml").getInt("factors.rating-spike", 20), "Rating spike");
            plugin.services().alerts().createAlert("RATING_SPIKE", com.meranked.model.AlertSeverity.HIGH,
                    "+" + Math.round(hourGain) + " rating in 1 hour", null, java.util.List.of(uuid));
        }
    }

    private record GainEntry(long timestamp, double amount) {}

    private void decayAll() {
        FileConfiguration config = configService.get("suspicion.yml");
        int decay = config.getInt("decay-per-day", 2);
        cache.replaceAll((uuid, score) -> Math.max(0, score - decay));
        cache.forEach(this::saveScore);
    }

    private int loadScore(UUID uuid) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT suspicion_score FROM ranked_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("suspicion_score");
                }
            }
            return 0;
        }).join();
    }

    private void saveScore(UUID uuid, int score) {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE ranked_players SET suspicion_score = ? WHERE uuid = ?")) {
                ps.setInt(1, score);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }
}
