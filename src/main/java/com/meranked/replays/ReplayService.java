package com.meranked.replays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.model.RankedMatch;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, List<ReplayEvent>> pendingEvents = new ConcurrentHashMap<>();

    public ReplayService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        plugin.tasks().runAsyncTimer(this::flushEvents, 20L, 20L);
    }

    public void recordEvent(String matchId, String description) {
        FileConfiguration config = configService.get("replays.yml");
        if (!config.getBoolean("enabled", true)) return;
        pendingEvents.computeIfAbsent(matchId, k -> new ArrayList<>())
                .add(new ReplayEvent(System.currentTimeMillis(), description));
    }

    public void recordCombatEvent(RankedMatch match, String type, String description) {
        recordEvent(match.matchId(), description);
    }

    public void saveMatch(RankedMatch match) {
        FileConfiguration config = configService.get("replays.yml");
        if (!config.getBoolean("enabled", true)) return;
        flushEventsForMatch(match.matchId());

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("matchId", match.matchId());
        data.put("gamemode", match.gamemode());
        data.put("arena", match.arenaName());
        data.put("winner", match.winner() == null ? "" : match.winner().toString());
        data.put("loser", match.loser() == null ? "" : match.loser().toString());
        data.put("duration", match.durationMillis());
        data.put("noRating", match.noRatingChange());
        data.put("matchQuality", match.matchQuality());
        data.put("matchQualityReason", match.matchQualityReason());
        data.put("bestOfThree", match.bestOfThree());
        data.put("stats", match.allStats());

        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                INSERT OR REPLACE INTO ranked_replays (match_id, data, created_at) VALUES (?, ?, ?)
                """))) {
                ps.setString(1, match.matchId());
                ps.setString(2, gson.toJson(data));
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    /** Loads the persisted combat timeline for a match (ordered by time). */
    public List<ReplayEvent> loadTimeline(String matchId) {
        return database.queryAsync(conn -> {
            List<ReplayEvent> events = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT timestamp_ms, description FROM ranked_replay_events WHERE match_id = ? ORDER BY timestamp_ms ASC")) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        events.add(new ReplayEvent(rs.getLong("timestamp_ms"), rs.getString("description")));
                    }
                }
            }
            return events;
        }).join();
    }

    /** Loads the stored replay summary payload for a match, or {@code null} if none. */
    public Map<String, Object> loadReplay(String matchId) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT data FROM ranked_replays WHERE match_id = ?")) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = gson.fromJson(rs.getString("data"), Map.class);
                        return data;
                    }
                }
            }
            return null;
        }).join();
    }

    public void printTimeline(org.bukkit.entity.Player viewer, String matchId) {
        plugin.tasks().runAsync(() -> {
            List<ReplayEvent> events = loadTimeline(matchId);
            plugin.tasks().runSync(() -> {
                viewer.sendMessage("§6§lCombat Timeline §7— §e" + matchId);
                if (events.isEmpty()) {
                    viewer.sendMessage("§7No recorded events for this match.");
                    return;
                }
                long start = events.get(0).timestamp();
                for (ReplayEvent event : events) {
                    long sec = (event.timestamp() - start) / 1000;
                    viewer.sendMessage("§8[" + formatTime(sec) + "] §7" + event.description());
                }
            });
        });
    }

    private String formatTime(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    private void flushEvents() {
        pendingEvents.keySet().forEach(this::flushEventsForMatch);
    }

    private void flushEventsForMatch(String matchId) {
        List<ReplayEvent> events = pendingEvents.remove(matchId);
        if (events == null || events.isEmpty()) return;
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ranked_replay_events (match_id, timestamp_ms, description) VALUES (?, ?, ?)")) {
                for (ReplayEvent event : events) {
                    ps.setString(1, matchId);
                    ps.setLong(2, event.timestamp());
                    ps.setString(3, event.description());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public record ReplayEvent(long timestamp, String description) {}
}
