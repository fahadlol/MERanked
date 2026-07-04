package com.meranked.matches;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loads match fairness history for the transparency dashboard. */
public final class FairnessDashboardService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;

    public FairnessDashboardService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
    }

    public boolean enabled() {
        return configService.get("fairness-dashboard.yml").getBoolean("fairness-dashboard.enabled", true);
    }

    public String pledge() {
        return configService.get("fairness-dashboard.yml").getString("fairness-dashboard.pledge", "");
    }

    public List<FairnessEntry> recentMatches(UUID uuid) {
        int limit = configService.get("fairness-dashboard.yml").getInt("fairness-dashboard.history-size", 8);
        return database.queryAsync(conn -> {
            List<FairnessEntry> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT m.match_id, m.gamemode, m.ended_at, q.quality, q.reason, q.rating_diff, q.ping_diff
                FROM ranked_matches m
                LEFT JOIN ranked_match_quality q ON m.match_id = q.match_id
                WHERE m.winner = ? OR m.loser = ?
                ORDER BY m.ended_at DESC LIMIT ?
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new FairnessEntry(
                                rs.getString("match_id"),
                                rs.getString("gamemode"),
                                rs.getLong("ended_at"),
                                rs.getInt("quality"),
                                rs.getString("reason"),
                                rs.getDouble("rating_diff"),
                                rs.getInt("ping_diff")
                        ));
                    }
                }
            }
            return list;
        }).join();
    }

    public record FairnessEntry(String matchId, String gamemode, long endedAt,
                                int quality, String reason, double ratingDiff, int pingDiff) {}
}
