package com.meranked.rating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Handles underdog/upset detection: extra rating for beating much higher-rated opponents,
 * plus tracking of upset wins and the highest-rated opponent beaten.
 */
public final class UpsetService {

    public enum UpsetLevel { NONE, UPSET, MAJOR }

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;

    public UpsetService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
    }

    public boolean enabled() {
        return configService.get("streak-pressure.yml").getBoolean("underdog.enabled", true);
    }

    public UpsetLevel classify(double winnerRatingBefore, double loserRatingBefore) {
        FileConfiguration cfg = configService.get("streak-pressure.yml");
        double diff = loserRatingBefore - winnerRatingBefore;
        if (diff >= cfg.getDouble("underdog.major-upset-threshold", 400)) return UpsetLevel.MAJOR;
        if (diff >= cfg.getDouble("underdog.upset-threshold", 200)) return UpsetLevel.UPSET;
        return UpsetLevel.NONE;
    }

    /** Extra rating to add on top of the base Glicko gain for an upset. Returns 0 for no upset. */
    public double bonusRating(UpsetLevel level, double baseGain) {
        if (level == UpsetLevel.NONE || baseGain <= 0) return 0;
        FileConfiguration cfg = configService.get("streak-pressure.yml");
        double mult = level == UpsetLevel.MAJOR
                ? cfg.getDouble("underdog.major-upset-bonus-multiplier", 1.30)
                : cfg.getDouble("underdog.upset-bonus-multiplier", 1.15);
        return baseGain * (mult - 1.0);
    }

    public void recordUpset(UUID uuid, String gamemode, double opponentRating, double diff) {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.dialect().upsetUpsert())) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                ps.setDouble(3, diff);
                ps.setDouble(4, opponentRating);
                ps.setDouble(5, diff);
                ps.setDouble(6, opponentRating);
                ps.executeUpdate();
            }
        });
    }

    public int upsetWins(UUID uuid, String gamemode) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT upset_wins FROM ranked_upsets WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("upset_wins");
                }
            }
            return 0;
        }).join();
    }

    public String upsetTitle() {
        return configService.get("streak-pressure.yml").getString("underdog.messages.upset-title", "UPSET VICTORY");
    }

    public String upsetSubtitle() {
        return configService.get("streak-pressure.yml").getString("underdog.messages.upset-subtitle",
                "You defeated a higher-ranked opponent.");
    }

    public String chatMessage(UpsetLevel level, long gain) {
        FileConfiguration cfg = configService.get("streak-pressure.yml");
        if (level == UpsetLevel.MAJOR) {
            return cfg.getString("underdog.messages.major-upset-chat", "");
        }
        return cfg.getString("underdog.messages.upset-chat", "").replace("<gain>", String.valueOf(gain));
    }
}
