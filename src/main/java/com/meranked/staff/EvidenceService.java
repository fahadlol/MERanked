package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates one-click evidence bundles for suspicious players/matches.
 */
public final class EvidenceService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public EvidenceService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public Bundle generateForMatch(UUID staff, String matchId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "MATCH");
        data.put("matchId", matchId);

        // match + participants
        services.database().executeSync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_matches WHERE match_id = ?")) {
                ps.setString(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.put("gamemode", rs.getString("gamemode"));
                        data.put("arena", rs.getString("arena"));
                        data.put("winner", nameOf(rs.getString("winner")));
                        data.put("loser", nameOf(rs.getString("loser")));
                        data.put("duration", rs.getLong("duration"));
                        data.put("noRating", rs.getBoolean("no_rating"));
                    }
                }
            }
            List<Map<String, Object>> participants = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_match_participants WHERE match_id = ?")) {
                ps.setString(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("player", nameOf(rs.getString("uuid")));
                        p.put("ratingBefore", rs.getDouble("rating_before"));
                        p.put("ratingAfter", rs.getDouble("rating_after"));
                        p.put("tierBefore", rs.getString("tier_before"));
                        p.put("tierAfter", rs.getString("tier_after"));
                        p.put("ping", rs.getInt("ping"));
                        participants.add(p);
                    }
                }
            }
            data.put("participants", participants);
            try (PreparedStatement ps = conn.prepareStatement("SELECT quality, reason FROM ranked_match_quality WHERE match_id = ?")) {
                ps.setString(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.put("matchQuality", rs.getInt("quality"));
                        data.put("matchQualityReason", rs.getString("reason"));
                    }
                }
            }
        });

        // replay timeline
        data.put("replayTimeline", services.replays().loadTimeline(matchId).stream()
                .map(e -> e.description()).toList());

        // notes
        data.put("matchNotes", services.staffNotes().getNotes("MATCH", matchId).stream().map(n -> n.text()).toList());

        String reason = "Match evidence bundle";
        return persist(staff, "MATCH", matchId, reason, 0, data);
    }

    public Bundle generateForPlayer(UUID staff, UUID target, String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "PLAYER");
        data.put("player", name);
        data.put("uuid", target.toString());

        int suspicion = services.suspicion().getScore(target);
        data.put("suspicion", suspicion);
        data.put("banned", services.bans().isBanned(target));
        data.put("playerNotes", services.staffNotes().getNotes("PLAYER", target.toString()).stream().map(n -> n.text()).toList());

        // recent matches
        List<String> matches = new ArrayList<>();
        services.database().executeSync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT match_id, gamemode, winner FROM ranked_matches
                WHERE winner = ? OR loser = ? ORDER BY ended_at DESC LIMIT 10
                """)) {
                ps.setString(1, target.toString());
                ps.setString(2, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        boolean won = target.toString().equals(rs.getString("winner"));
                        matches.add(rs.getString("match_id") + " " + rs.getString("gamemode") + " " + (won ? "W" : "L"));
                    }
                }
            }
        });
        data.put("recentMatches", matches);

        String reason = "Player evidence bundle (suspicion " + suspicion + "/100)";
        return persist(staff, "PLAYER", target.toString(), reason, suspicion, data);
    }

    private Bundle persist(UUID staff, String targetType, String targetId, String reason, int suspicion,
                           Map<String, Object> data) {
        String bundleId = "E" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String json = gson.toJson(data);
        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_evidence_bundles
                (bundle_id, target_type, target_id, reason, suspicion, data, created_by, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, bundleId);
                ps.setString(2, targetType);
                ps.setString(3, targetId);
                ps.setString(4, reason);
                ps.setInt(5, suspicion);
                ps.setString(6, json);
                ps.setString(7, staff == null ? "CONSOLE" : staff.toString());
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });

        FileConfiguration cfg = services.config().get("evidence.yml");
        // JSON export to disk
        if (cfg.getBoolean("evidence.outputs.json-export", true)) {
            try {
                File folder = new File(plugin.getDataFolder(), cfg.getString("evidence.json-export-folder", "evidence"));
                folder.mkdirs();
                Files.write(new File(folder, bundleId + ".json").toPath(), json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to write evidence bundle: " + ex.getMessage());
            }
        }
        // Discord
        if (cfg.getBoolean("evidence.outputs.discord-webhook", false)) {
            String url = services.config().get("staff-alerts.yml").getString("discord.webhook-url", "");
            services.discord().sendRaw(url, "**Evidence Bundle** `" + bundleId + "`\\n" + reason);
        }

        return new Bundle(bundleId, targetType, targetId, reason, suspicion, json);
    }

    public String textSummary(Bundle bundle) {
        return "Evidence Bundle #" + bundle.bundleId()
                + "\nTarget: " + bundle.targetId()
                + "\nReason: " + bundle.reason()
                + "\nSuspicion: " + bundle.suspicion() + "/100";
    }

    private String nameOf(String uuid) {
        if (uuid == null || uuid.isEmpty()) return "N/A";
        try {
            String n = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return n == null ? uuid : n;
        } catch (IllegalArgumentException ex) {
            return uuid;
        }
    }

    public record Bundle(String bundleId, String targetType, String targetId, String reason, int suspicion, String json) {}

    public java.util.List<BundleSummary> recentBundles(int limit) {
        return services.database().queryAsync(conn -> {
            java.util.List<BundleSummary> list = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT bundle_id, target_type, target_id, reason, suspicion, created_at
                FROM ranked_evidence_bundles ORDER BY created_at DESC LIMIT ?
                """)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new BundleSummary(
                                rs.getString("bundle_id"),
                                rs.getString("target_type"),
                                rs.getString("target_id"),
                                rs.getString("reason"),
                                rs.getInt("suspicion"),
                                rs.getLong("created_at")));
                    }
                }
            }
            return list;
        }).join();
    }

    public record BundleSummary(String bundleId, String targetType, String targetId,
                                String reason, int suspicion, long createdAt) {}
}
