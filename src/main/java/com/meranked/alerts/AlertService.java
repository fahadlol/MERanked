package com.meranked.alerts;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.model.AlertSeverity;
import com.meranked.model.RankedMatch;
import com.meranked.model.StaffAlert;
import com.meranked.staff.SuspicionService;
import com.meranked.util.IdUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AlertService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final MessageService messages;
    private final SuspicionService suspicionService;
    private final List<StaffAlert> recentAlerts = new CopyOnWriteArrayList<>();

    public AlertService(MERankedPlugin plugin, ConfigService configService, DatabaseService database,
                        MessageService messages, SuspicionService suspicionService) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.messages = messages;
        this.suspicionService = suspicionService;
    }

    public StaffAlert createAlert(String type, AlertSeverity severity, String reason, RankedMatch match, List<UUID> players) {
        String alertId = IdUtil.newAlertId();
        String playerNames = players.stream().map(u -> Bukkit.getOfflinePlayer(u).getName()).reduce((a, b) -> a + ", " + b).orElse("");
        StaffAlert alert = new StaffAlert(
                alertId, type, severity, reason, match == null ? "" : match.matchId(),
                match == null ? "" : match.gamemode(), match == null ? "" : match.arenaName(),
                playerNames, System.currentTimeMillis(), false, null
        );
        recentAlerts.add(0, alert);
        if (recentAlerts.size() > 100) recentAlerts.remove(recentAlerts.size() - 1);

        saveAlertAsync(alert);
        broadcastAlert(alert);
        plugin.services().discord().sendAlert(alert);
        return alert;
    }

    private void broadcastAlert(StaffAlert alert) {
        FileConfiguration config = configService.get("staff-alerts.yml");
        AlertSeverity min = AlertSeverity.valueOf(config.getString("in-game.min-severity", "LOW"));
        if (!alert.severity().isAtLeast(min)) return;

        Map<String, String> ph = Map.of(
                "type", alert.type(),
                "players", alert.players(),
                "match_id", alert.matchId() == null ? "N/A" : alert.matchId(),
                "reason", alert.reason()
        );
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("meranked.staff")) {
                messages.sendPrefixed(staff, "staff.alert", ph);
            }
        }
    }

    public List<StaffAlert> recentAlerts() {
        return List.copyOf(recentAlerts);
    }

    public void resolveAlert(String alertId) {
        recentAlerts.stream().filter(a -> a.alertId().equals(alertId)).findFirst().ifPresent(a -> {
            recentAlerts.remove(a);
            recentAlerts.add(new StaffAlert(a.alertId(), a.type(), a.severity(), a.reason(), a.matchId(),
                    a.gamemode(), a.arena(), a.players(), a.createdAt(), true, a.flaggedBy()));
        });
        database.executeAsync(conn -> {
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE ranked_alerts SET resolved = TRUE WHERE alert_id = ?")) {
                    ps.setString(1, alertId);
                    ps.executeUpdate();
                }
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().severe("Failed to resolve alert: " + ex.getMessage());
            }
        });
    }

    /** Resolves all unresolved alerts tied to a finished match (used after rollbacks). */
    public void resolveAlertsForMatch(String matchId) {
        if (matchId == null || matchId.isEmpty()) return;
        recentAlerts.stream()
                .filter(a -> matchId.equals(a.matchId()) && !a.resolved())
                .map(StaffAlert::alertId)
                .toList()
                .forEach(this::resolveAlert);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ranked_alerts SET resolved = TRUE WHERE match_id = ? AND resolved = FALSE")) {
                ps.setString(1, matchId);
                ps.executeUpdate();
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().severe("Failed to resolve match alerts: " + ex.getMessage());
            }
        });
    }

    private void saveAlertAsync(StaffAlert alert) {
        database.executeAsync(conn -> {
            try {
                try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                    INSERT OR REPLACE INTO ranked_alerts
                    (alert_id, alert_type, severity, reason, match_id, gamemode, arena, players, created_at, resolved, flagged_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """))) {
                    ps.setString(1, alert.alertId());
                    ps.setString(2, alert.type());
                    ps.setString(3, alert.severity().name());
                    ps.setString(4, alert.reason());
                    ps.setString(5, alert.matchId());
                    ps.setString(6, alert.gamemode());
                    ps.setString(7, alert.arena());
                    ps.setString(8, alert.players());
                    ps.setLong(9, alert.createdAt());
                    ps.setBoolean(10, alert.resolved());
                    ps.setString(11, alert.flaggedBy() == null ? null : alert.flaggedBy().toString());
                    ps.executeUpdate();
                }
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().severe("Failed to save alert: " + ex.getMessage());
            }
        });
    }
}
