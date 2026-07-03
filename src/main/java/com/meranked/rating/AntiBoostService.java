    package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiBoostService {

    private final ConfigService configService;
    private final DatabaseService database;
    private final ConcurrentHashMap<String, Integer> sameIpCache = new ConcurrentHashMap<>();

    public AntiBoostService(ConfigService configService, DatabaseService database) {
        this.configService = configService;
        this.database = database;
    }

    public ValidationResult validate(UUID p1, UUID p2, String ip1, String ip2, long matchDurationMs) {
        FileConfiguration config = configService.get("anti-boost.yml");
        if (config.getBoolean("same-ip.no-rating", true) && ip1 != null && ip1.equals(ip2)) {
            return ValidationResult.invalid("SAME_IP");
        }
        long minDuration = config.getLong("short-match.min-duration-seconds", 30) * 1000;
        if (config.getBoolean("short-match.no-rating", true) && matchDurationMs < minDuration) {
            return ValidationResult.invalid("SHORT_MATCH");
        }
        int maxPerDay = config.getInt("same-opponent.max-rated-matches-per-day", 2);
        if (config.getBoolean("same-opponent.no-rating-after-limit", true)) {
            int count = getOpponentMatchCount(p1, p2);
            if (count >= maxPerDay) {
                return ValidationResult.invalid("OPPONENT_LIMIT");
            }
        }
        return ValidationResult.valid();
    }

    public void recordOpponentMatch(UUID p1, UUID p2) {
        database.executeAsync(conn -> {
            upsertOpponent(conn, p1, p2);
            upsertOpponent(conn, p2, p1);
        });
    }

    private void upsertOpponent(java.sql.Connection conn, UUID uuid, UUID opponent) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO ranked_opponent_limits (uuid, opponent_uuid, match_count, last_match)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(uuid, opponent_uuid) DO UPDATE SET
            match_count = CASE WHEN last_match < ? THEN 1 ELSE match_count + 1 END,
            last_match = ?
            """)) {
            long now = System.currentTimeMillis();
            long dayAgo = now - 86400000L;
            ps.setString(1, uuid.toString());
            ps.setString(2, opponent.toString());
            ps.setLong(3, now);
            ps.setLong(4, dayAgo);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    /** Public accessor for how many rated matches these two have had in the last 24h. */
    public int recentOpponentCount(UUID p1, UUID p2) {
        return getOpponentMatchCount(p1, p2);
    }

    private int getOpponentMatchCount(UUID p1, UUID p2) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT match_count, last_match FROM ranked_opponent_limits WHERE uuid = ? AND opponent_uuid = ?")) {
                ps.setString(1, p1.toString());
                ps.setString(2, p2.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long last = rs.getLong("last_match");
                        if (System.currentTimeMillis() - last > 86400000L) return 0;
                        return rs.getInt("match_count");
                    }
                }
            }
            return 0;
        }).join();
    }

    public record ValidationResult(boolean allowed, String reason) {
        public static ValidationResult valid() { return new ValidationResult(true, null); }
        public static ValidationResult invalid(String reason) { return new ValidationResult(false, reason); }
    }
}
