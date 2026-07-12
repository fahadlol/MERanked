package com.meranked.queue;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.model.QueueEntry;
import com.meranked.rating.ProfileService;
import com.meranked.rating.TierService;
import com.meranked.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiDodgeService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final MessageService messages;
    private final Map<UUID, DodgeRecord> cache = new ConcurrentHashMap<>();

    public AntiDodgeService(MERankedPlugin plugin, ConfigService configService,
                            DatabaseService database, MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.messages = messages;
    }

    public boolean canQueue(UUID uuid) {
        DodgeRecord record = getRecord(uuid);
        long now = System.currentTimeMillis();
        if (record.hiddenUntil() > now) return false;
        return record.cooldownUntil() <= now;
    }

    public Optional<String> queueBlockReason(UUID uuid) {
        DodgeRecord record = getRecord(uuid);
        long now = System.currentTimeMillis();
        if (record.hiddenUntil() > now) {
            return Optional.of("hidden:" + (record.hiddenUntil() - now));
        }
        if (record.cooldownUntil() > now) {
            return Optional.of("cooldown:" + (record.cooldownUntil() - now));
        }
        return Optional.empty();
    }

    public void recordDodge(UUID uuid) {
        DodgeRecord record = getRecord(uuid);
        int newCount = record.dodgeCount() + 1;
        long cooldown = calculateCooldown(newCount);
        long hiddenUntil = calculateHiddenUntil(newCount);
        DodgeRecord updated = new DodgeRecord(newCount, cooldown, hiddenUntil, record.hiddenReason(), System.currentTimeMillis());
        cache.put(uuid, updated);
        saveAsync(uuid, updated);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (hiddenUntil > System.currentTimeMillis()) {
                messages.sendPrefixed(player, "queue.hidden", Map.of(
                        "time", TextUtil.formatDuration((hiddenUntil - System.currentTimeMillis()) / 1000)
                ));
            } else if (cooldown > System.currentTimeMillis()) {
                messages.sendPrefixed(player, "dodge.penalty", Map.of(
                        "time", TextUtil.formatDuration((cooldown - System.currentTimeMillis()) / 1000)
                ));
            }
        }
    }

    public void clearDodges(UUID uuid) {
        cache.put(uuid, DodgeRecord.empty());
        saveAsync(uuid, DodgeRecord.empty());
    }

    public void hideQueue(UUID uuid, long durationMs, String reason) {
        DodgeRecord record = getRecord(uuid);
        DodgeRecord updated = new DodgeRecord(record.dodgeCount(), record.cooldownUntil(),
                System.currentTimeMillis() + durationMs, reason, record.lastDodge());
        cache.put(uuid, updated);
        saveAsync(uuid, updated);
    }

    public void unhideQueue(UUID uuid) {
        DodgeRecord record = getRecord(uuid);
        cache.put(uuid, new DodgeRecord(record.dodgeCount(), record.cooldownUntil(), 0, null, record.lastDodge()));
        saveAsync(uuid, getRecord(uuid));
    }

    public DodgeRecord getRecord(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadRecord);
    }

    private long calculateCooldown(int count) {
        FileConfiguration config = configService.get("anti-dodge.yml");
        var list = config.getMapList("cooldowns");
        long seconds = 30;
        for (var entry : list) {
            int c = ((Number) entry.get("count")).intValue();
            if (count >= c) seconds = ((Number) entry.get("duration-seconds")).longValue();
        }
        return System.currentTimeMillis() + seconds * 1000;
    }

    private long calculateHiddenUntil(int count) {
        FileConfiguration config = configService.get("anti-dodge.yml");
        var list = config.getMapList("cooldowns");
        for (var entry : list) {
            int c = ((Number) entry.get("count")).intValue();
            if (count >= c && entry.containsKey("hide-duration-seconds")) {
                long hide = ((Number) entry.get("hide-duration-seconds")).longValue();
                if (hide > 0) return System.currentTimeMillis() + hide * 1000;
            }
        }
        return 0;
    }

    private DodgeRecord loadRecord(UUID uuid) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_dodges WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new DodgeRecord(
                                rs.getInt("dodge_count"),
                                rs.getLong("cooldown_until"),
                                rs.getLong("hidden_until"),
                                rs.getString("hidden_reason"),
                                rs.getLong("last_dodge")
                        );
                    }
                }
            }
            return DodgeRecord.empty();
        }).join();
    }

    private void saveAsync(UUID uuid, DodgeRecord record) {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                INSERT OR REPLACE INTO ranked_dodges
                (uuid, dodge_count, cooldown_until, hidden_until, hidden_reason, last_dodge)
                VALUES (?,?,?,?,?,?)
                """))) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, record.dodgeCount());
                ps.setLong(3, record.cooldownUntil());
                ps.setLong(4, record.hiddenUntil());
                ps.setString(5, record.hiddenReason());
                ps.setLong(6, record.lastDodge());
                ps.executeUpdate();
            }
        });
    }

    public record DodgeRecord(int dodgeCount, long cooldownUntil, long hiddenUntil, String hiddenReason, long lastDodge) {
        public static DodgeRecord empty() {
            return new DodgeRecord(0, 0, 0, null, 0);
        }
    }
}
