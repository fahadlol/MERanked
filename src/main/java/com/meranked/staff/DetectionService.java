package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.AlertSeverity;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Behavioural abuse detectors: friend farming, queue ghosting and suspicious region switching.
 */
public final class DetectionService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;

    public DetectionService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    // ---- Friend farming ----

    /** Called after a rated match; counts cross-gamemode matches between the pair in the window. */
    public void recordMatch(UUID a, UUID b, String gamemode) {
        FileConfiguration cfg = services.config().get("friend-farming.yml");
        if (!cfg.getBoolean("friend-farming.enabled", true)) return;
        long windowMs = cfg.getLong("friend-farming.window-hours", 24) * 3600_000L;
        long now = System.currentTimeMillis();

        services.database().executeAsync(conn -> {
            upsertFarming(conn, a, b, gamemode, now, windowMs);
            upsertFarming(conn, b, a, gamemode, now, windowMs);

            int total = 0;
            int gamemodes = 0;
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT gamemode, match_count FROM ranked_friend_farming
                WHERE uuid = ? AND opponent_uuid = ? AND window_start > ?
                """)) {
                ps.setString(1, a.toString());
                ps.setString(2, b.toString());
                ps.setLong(3, now - windowMs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        total += rs.getInt("match_count");
                        gamemodes++;
                    }
                }
            }

            int max = cfg.getInt("friend-farming.max-cross-gamemode-matches-24h", 6);
            if (total > max && gamemodes >= 2) {
                int fTotal = total;
                int fModes = gamemodes;
                Bukkit.getScheduler().runTask(plugin, () -> alertFarming(a, b, fTotal, fModes, cfg));
            }
        });
    }

    private void upsertFarming(java.sql.Connection conn, UUID uuid, UUID opp, String gamemode,
                               long now, long windowMs) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(services.database().dialect().friendFarmingUpsert())) {
            ps.setString(1, uuid.toString());
            ps.setString(2, opp.toString());
            ps.setString(3, gamemode);
            ps.setLong(4, now);
            ps.setLong(5, now - windowMs);
            ps.setLong(6, now - windowMs);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    private void alertFarming(UUID a, UUID b, int total, int modes, FileConfiguration cfg) {
        AlertSeverity severity = severity(cfg.getString("friend-farming.alert-severity", "HIGH"));
        String reason = total + " ranked matches across " + modes + " gamemodes in 24h.";
        services.alerts().createAlert("FRIEND_FARMING", severity, reason, null, java.util.List.of(a, b));
        int inc = cfg.getInt("friend-farming.suspicion-increase", 15);
        services.suspicion().addScore(a, inc, "Friend farming");
        services.suspicion().addScore(b, inc, "Friend farming");
    }

    // ---- Queue ghosting ----

    /** Called when a player leaves queue; records if a potential opponent was queued at the time. */
    public void recordQueueLeave(UUID leaver, String gamemode) {
        FileConfiguration cfg = services.config().get("queue-ghosting.yml");
        if (!cfg.getBoolean("queue-ghosting.enabled", true)) return;

        UUID avoided = services.queue().getQueue(gamemode).stream()
                .map(e -> e.uuid())
                .filter(u -> !u.equals(leaver))
                .findFirst().orElse(null);
        if (avoided == null) return;

        long windowMs = cfg.getLong("queue-ghosting.window-minutes", 30) * 60_000L;
        long now = System.currentTimeMillis();
        int threshold = cfg.getInt("queue-ghosting.leave-threshold", 5);

        services.database().executeAsync(conn -> {
            int count;
            try (PreparedStatement ps = conn.prepareStatement(services.database().dialect().queueGhostingUpsert())) {
                ps.setString(1, leaver.toString());
                ps.setString(2, avoided.toString());
                ps.setLong(3, now);
                ps.setLong(4, now - windowMs);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT leave_count FROM ranked_queue_ghosting WHERE uuid = ? AND avoided_uuid = ?")) {
                ps.setString(1, leaver.toString());
                ps.setString(2, avoided.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    count = rs.next() ? rs.getInt("leave_count") : 1;
                }
            }
            if (count >= threshold) {
                int fc = count;
                Bukkit.getScheduler().runTask(plugin, () -> alertGhosting(leaver, avoided, fc, cfg));
            }
        });
    }

    private void alertGhosting(UUID leaver, UUID avoided, int count, FileConfiguration cfg) {
        AlertSeverity severity = severity(cfg.getString("queue-ghosting.alert-severity", "MEDIUM"));
        String avoidedName = Bukkit.getOfflinePlayer(avoided).getName();
        String reason = "Left queue " + count + " times when " + avoidedName + " was queueing.";
        services.alerts().createAlert("QUEUE_GHOSTING", severity, reason, null, java.util.List.of(leaver));
        services.suspicion().addScore(leaver, cfg.getInt("queue-ghosting.suspicion-increase", 10), "Queue ghosting");
    }

    // ---- Region switching ----

    public void recordRegionChange(UUID uuid, String oldRegion, String newRegion) {
        FileConfiguration cfg = services.config().get("alt-lock.yml");
        long now = System.currentTimeMillis();
        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_region_history (uuid, old_region, new_region, changed_at)
                VALUES (?,?,?,?)
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, oldRegion);
                ps.setString(3, newRegion);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            if (!cfg.getBoolean("region-switching.alert-on-suspicious-switching", true)) return;
            int perWeek = cfg.getInt("region-switching.suspicious-changes-per-week", 2);
            int changes;
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) AS c FROM ranked_region_history WHERE uuid = ? AND changed_at > ?
                """)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, now - 7L * 86400_000L);
                try (ResultSet rs = ps.executeQuery()) {
                    changes = rs.next() ? rs.getInt("c") : 0;
                }
            }
            if (changes > perWeek) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    AlertSeverity severity = severity(cfg.getString("region-switching.alert-severity", "MEDIUM"));
                    String reason = changes + " region changes in the last week (" + oldRegion + " -> " + newRegion + ").";
                    services.alerts().createAlert("REGION_SWITCHING", severity, reason, null, java.util.List.of(uuid));
                });
            }
        });
    }

    private AlertSeverity severity(String name) {
        try {
            return AlertSeverity.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AlertSeverity.MEDIUM;
        }
    }
}
