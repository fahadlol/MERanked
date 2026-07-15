package com.meranked.replays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.model.RankedMatch;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

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

    public boolean visualReplayEnabled() {
        return configService.get("replays.yml").getBoolean("visual-replay-enabled", true);
    }

    public void recordEvent(String matchId, String description) {
        recordEvent(matchId, "UNKNOWN", description);
    }

    public void recordEvent(String matchId, String eventType, String description) {
        FileConfiguration config = configService.get("replays.yml");
        if (!config.getBoolean("enabled", true)) return;
        pendingEvents.computeIfAbsent(matchId, k -> new ArrayList<>())
                .add(new ReplayEvent(System.currentTimeMillis(), eventType, description));
    }

    public void recordCombatEvent(RankedMatch match, String type, String description) {
        recordEvent(match.matchId(), type, description);
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
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_replays (match_id, data, created_at) VALUES (?, ?, ?)
                """)) {
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
                    "SELECT timestamp_ms, event_type, description FROM ranked_replay_events WHERE match_id = ? ORDER BY timestamp_ms ASC")) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("event_type");
                        if (type == null || type.isBlank()) type = "UNKNOWN";
                        events.add(new ReplayEvent(rs.getLong("timestamp_ms"), type, rs.getString("description")));
                    }
                }
            } catch (java.sql.SQLException ex) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT timestamp_ms, description FROM ranked_replay_events WHERE match_id = ? ORDER BY timestamp_ms ASC")) {
                    ps.setString(1, matchId);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String desc = rs.getString("description");
                            events.add(new ReplayEvent(rs.getLong("timestamp_ms"), inferType(desc), desc));
                        }
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

    public void printTimeline(Player viewer, String matchId) {
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

    public Material iconForType(String eventType) {
        if (eventType == null) return Material.PAPER;
        return switch (eventType.toUpperCase()) {
            case "DAMAGE_DEALT", "HIT_LANDED", "CRITICAL_HIT" -> Material.IRON_SWORD;
            case "TOTEM_POP" -> Material.TOTEM_OF_UNDYING;
            case "CRYSTAL_PLACED", "CRYSTAL_BROKEN" -> Material.END_CRYSTAL;
            case "ANCHOR_USED" -> Material.RESPAWN_ANCHOR;
            case "OBSIDIAN_PLACED" -> Material.OBSIDIAN;
            case "PEARL_THROWN" -> Material.ENDER_PEARL;
            case "GOLDEN_APPLE_EATEN" -> Material.GOLDEN_APPLE;
            case "POTION_USED" -> Material.SPLASH_POTION;
            case "DEATH", "DISCONNECT" -> Material.SKELETON_SKULL;
            default -> Material.PAPER;
        };
    }

    private String inferType(String description) {
        if (description == null) return "UNKNOWN";
        String lower = description.toLowerCase();
        if (lower.contains("damage")) return "DAMAGE_DEALT";
        if (lower.contains("totem")) return "TOTEM_POP";
        if (lower.contains("crystal")) return "CRYSTAL_PLACED";
        if (lower.contains("death") || lower.contains("died")) return "DEATH";
        if (lower.contains("disconnect") || lower.contains("quit")) return "DISCONNECT";
        return "UNKNOWN";
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
                    "INSERT INTO ranked_replay_events (match_id, timestamp_ms, event_type, description) VALUES (?, ?, ?, ?)")) {
                for (ReplayEvent event : events) {
                    ps.setString(1, matchId);
                    ps.setLong(2, event.timestamp());
                    ps.setString(3, event.eventType());
                    ps.setString(4, event.description());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (java.sql.SQLException ex) {
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
            }
        });
    }

    public record ReplayEvent(long timestamp, String eventType, String description) {}
}
