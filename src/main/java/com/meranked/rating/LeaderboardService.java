package com.meranked.rating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LeaderboardService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final TierService tierService;
    private final java.util.Map<String, CachedRank> rankCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RANK_TTL_MS = 30_000L;

    public LeaderboardService(MERankedPlugin plugin, ConfigService configService,
                              DatabaseService database, TierService tierService) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.tierService = tierService;
    }

    public List<LeaderboardEntry> getTop(String gamemode, int limit) {
        return getTop(gamemode, null, limit);
    }

    public List<LeaderboardEntry> getTop(String gamemode, String region, int limit) {
        return database.queryAsync(conn -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = region == null
                    ? """
                    SELECT p.username, p.region, p.region_hidden, pr.tier, pr.rating, pr.wins, pr.losses
                    FROM ranked_profiles pr
                    JOIN ranked_players p ON p.uuid = pr.uuid
                    WHERE pr.gamemode = ? AND pr.ranked = TRUE
                    ORDER BY pr.rating DESC LIMIT ?
                    """
                    : """
                    SELECT p.username, p.region, p.region_hidden, pr.tier, pr.rating, pr.wins, pr.losses
                    FROM ranked_profiles pr
                    JOIN ranked_players p ON p.uuid = pr.uuid
                    WHERE pr.gamemode = ? AND pr.ranked = TRUE AND p.region = ? AND p.region_hidden = FALSE
                    ORDER BY pr.rating DESC LIMIT ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gamemode);
                if (region == null) ps.setInt(2, limit);
                else {
                    ps.setString(2, region);
                    ps.setInt(3, limit);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        String regionTag = rs.getBoolean("region_hidden") ? "Hidden" : rs.getString("region");
                        entries.add(new LeaderboardEntry(
                                rank++,
                                rs.getString("username"),
                                regionTag,
                                rs.getString("tier"),
                                rs.getDouble("rating"),
                                rs.getInt("wins"),
                                rs.getInt("losses")
                        ));
                    }
                }
            }
            return entries;
        }).join();
    }

    /** Cached rank lookup safe to call frequently (e.g. from placeholders/scoreboards). */
    public int getRankCached(UUID uuid, String gamemode) {
        String key = uuid + ":" + gamemode;
        CachedRank cached = rankCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp() < RANK_TTL_MS) {
            return cached.rank();
        }
        // Refresh asynchronously; return stale value (or 0) immediately to avoid blocking the main thread.
        plugin.tasks().runAsync(() -> {
            int rank = getRank(uuid, gamemode);
            rankCache.put(key, new CachedRank(rank, System.currentTimeMillis()));
        });
        return cached == null ? 0 : cached.rank();
    }

    public int getRank(UUID uuid, String gamemode) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) + 1 AS rank FROM ranked_profiles
                WHERE gamemode = ? AND ranked = TRUE AND rating > (
                    SELECT rating FROM ranked_profiles WHERE uuid = ? AND gamemode = ?
                )
                """)) {
                ps.setString(1, gamemode);
                ps.setString(2, uuid.toString());
                ps.setString(3, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("rank");
                }
            }
            return 0;
        }).join();
    }

    private record CachedRank(int rank, long timestamp) {}

    public record LeaderboardEntry(int rank, String username, String region, String tier, double rating, int wins, int losses) {}
}
