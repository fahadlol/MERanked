package com.meranked.matches;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import com.meranked.rating.TierService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Computes an informational match quality/fairness percentage after a match,
 * based on rating gap, confidence gap, ping gap, recent-opponent frequency and queue range.
 */
public final class MatchQualityService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final TierService tierService;

    public MatchQualityService(MERankedPlugin plugin, ConfigService configService,
                               DatabaseService database, TierService tierService) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.tierService = tierService;
    }

    public boolean enabled() {
        return configService.get("match-quality.yml").getBoolean("match-quality.enabled", true);
    }

    public QualityResult evaluate(RankedMatch match, RankedProfile p1, RankedProfile p2,
                                  int ping1, int ping2, int recentMatches) {
        FileConfiguration cfg = configService.get("match-quality.yml");
        double maxRating = cfg.getDouble("match-quality.max-rating-diff", 400);
        double maxConf = cfg.getDouble("match-quality.max-confidence-diff", 200);
        double maxPing = cfg.getDouble("match-quality.max-ping-diff", 200);
        double maxRange = cfg.getDouble("match-quality.max-queue-range", 400);

        double ratingDiff = Math.abs(p1.rating() - p2.rating());
        double confDiff = Math.abs(p1.ratingDeviation() - p2.ratingDeviation());
        int pingDiff = Math.abs(ping1 - ping2);
        int queueRange = match.queueRange();

        double wRating = cfg.getDouble("match-quality.weights.rating-diff", 0.35);
        double wConf = cfg.getDouble("match-quality.weights.confidence-diff", 0.2);
        double wPing = cfg.getDouble("match-quality.weights.ping-diff", 0.15);
        double wRecent = cfg.getDouble("match-quality.weights.recent-opponent", 0.15);
        double wRange = cfg.getDouble("match-quality.weights.queue-range", 0.15);

        double penalty =
                wRating * clamp(ratingDiff / maxRating) +
                wConf * clamp(confDiff / maxConf) +
                wPing * clamp(pingDiff / maxPing) +
                wRecent * clamp(recentMatches / 3.0) +
                wRange * clamp(queueRange / maxRange);

        int quality = (int) Math.round((1.0 - penalty) * 100);
        quality = Math.max(0, Math.min(100, quality));

        String reason;
        String base = "match-quality.reasons.";
        if (queueRange >= maxRange * 0.75 && ratingDiff > maxRating * 0.5) {
            reason = cfg.getString(base + "poor", "Wide rating range due to long queue time.");
        } else if (quality >= 90) {
            reason = cfg.getString(base + "excellent", "Similar rating and stable confidence.");
        } else if (quality >= 75) {
            reason = cfg.getString(base + "good", "Balanced match.");
        } else if (quality >= 55) {
            reason = cfg.getString(base + "fair", "Some rating or confidence gap.");
        } else {
            reason = cfg.getString(base + "poor", "Wide rating range due to long queue time.");
        }

        return new QualityResult(quality, reason, ratingDiff, confDiff, pingDiff, queueRange);
    }

    public void store(String matchId, QualityResult result) {
        FileConfiguration cfg = configService.get("match-quality.yml");
        if (!cfg.getBoolean("match-quality.store-in-history", true)) return;
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_match_quality
                (match_id, quality, reason, rating_diff, confidence_diff, ping_diff, queue_range)
                VALUES (?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, matchId);
                ps.setInt(2, result.quality());
                ps.setString(3, result.reason());
                ps.setDouble(4, result.ratingDiff());
                ps.setDouble(5, result.confidenceDiff());
                ps.setInt(6, result.pingDiff());
                ps.setInt(7, result.queueRange());
                ps.executeUpdate();
            }
        });
    }

    public int loadQuality(String matchId) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT quality FROM ranked_match_quality WHERE match_id = ?")) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("quality");
                }
            }
            return -1;
        }).join();
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1.0, v));
    }

    public record QualityResult(int quality, String reason, double ratingDiff,
                                double confidenceDiff, int pingDiff, int queueRange) {}
}
