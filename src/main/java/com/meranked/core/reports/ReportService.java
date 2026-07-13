package com.meranked.core.reports;

import com.google.gson.JsonObject;
import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import com.meranked.core.logs.MerankedLogEvent;
import com.meranked.core.logs.MerankedLoggerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportService {

    public enum Status { OPEN, REVIEWED, VALID, INVALID }

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final MerankedLoggerService logger;
    private final ConfigService configService;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ReportService(MERankedPlugin plugin, ServiceRegistry services, MerankedLoggerService logger,
                         ConfigService configService) {
        this.plugin = plugin;
        this.services = services;
        this.logger = logger;
        this.configService = configService;
    }

    public boolean enabled() {
        return cfg().getBoolean("reports.enabled", true);
    }

    public Optional<String> createReport(Player reporter, String targetName, String reason) {
        if (!enabled()) return Optional.of("Reports are disabled.");
        if (reason.length() < cfg().getInt("reports.min-reason-length", 5)) {
            return Optional.of("Reason is too short.");
        }
        if (cfg().getBoolean("reports.block-self-report", true)
                && reporter.getName().equalsIgnoreCase(targetName)) {
            return Optional.of("You cannot report yourself.");
        }
        int cooldownSec = cfg().getInt("reports.cooldown-seconds", 120);
        Long last = cooldowns.get(reporter.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownSec * 1000L) {
            return Optional.of("Please wait before reporting again.");
        }

        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        String reportId = "RPT-" + Long.toString(now, 36).toUpperCase();

        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_reports
                (report_id, reporter_uuid, reporter_name, reported_uuid, reported_name, reason, status, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, reportId);
                ps.setString(2, reporter.getUniqueId().toString());
                ps.setString(3, reporter.getName());
                ps.setString(4, targetUuid.toString());
                ps.setString(5, targetName);
                ps.setString(6, reason);
                ps.setString(7, Status.OPEN.name());
                ps.setLong(8, now);
                ps.executeUpdate();
            }
        });

        cooldowns.put(reporter.getUniqueId(), now);
        logger.log(MerankedLogEvent.builder(loggerServerId(), LogCategory.REPORT, "REPORT_CREATED", LogSeverity.MEDIUM,
                        reporter.getName() + " reported " + targetName + ": " + reason)
                .player(targetName, targetUuid)
                .staff(reporter.getName(), reporter.getUniqueId())
                .put("reportId", reportId)
                .put("reason", reason)
                .put("world", reporter.getWorld().getName())
                .build());
        return Optional.empty();
    }

    public List<ReportRecord> openReports() {
        return services.database().queryAsync(conn -> {
            List<ReportRecord> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM ranked_reports WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 50");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(read(rs));
            }
            return list;
        }).join();
    }

    public void review(String reportId, Player staff, Status status, String notes) {
        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE ranked_reports SET status = ?, reviewed_by = ?, reviewed_at = ?, review_notes = ?
                WHERE report_id = ?
                """)) {
                ps.setString(1, status.name());
                ps.setString(2, staff.getUniqueId().toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, notes);
                ps.setString(5, reportId);
                ps.executeUpdate();
            }
        });
        String type = switch (status) {
            case VALID -> "REPORT_MARKED_VALID";
            case INVALID -> "REPORT_MARKED_INVALID";
            default -> "REPORT_REVIEWED";
        };
        logger.logStaff(LogCategory.REPORT, type, LogSeverity.MEDIUM,
                "Report " + reportId + " marked " + status.name() + (notes != null ? ": " + notes : ""),
                staff.getName(), staff.getUniqueId(),
                Map.of("reportId", reportId, "status", status.name()));
    }

    public JsonObject summaryForPlayer(UUID uuid) {
        JsonObject summary = new JsonObject();
        int count = services.database().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ranked_reports WHERE reported_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }).join();
        summary.addProperty("reportCount", count);
        return summary;
    }

    private ReportRecord read(ResultSet rs) throws java.sql.SQLException {
        return new ReportRecord(
                rs.getString("report_id"),
                UUID.fromString(rs.getString("reporter_uuid")),
                rs.getString("reporter_name"),
                UUID.fromString(rs.getString("reported_uuid")),
                rs.getString("reported_name"),
                rs.getString("reason"),
                Status.valueOf(rs.getString("status")),
                rs.getLong("created_at"));
    }

    private String loggerServerId() {
        return cfg().getString("discord-bridge.server-id", "meranked-main");
    }

    private org.bukkit.configuration.file.FileConfiguration cfg() {
        return configService.get("config.yml");
    }

    public record ReportRecord(String reportId, UUID reporterUuid, String reporterName,
                               UUID reportedUuid, String reportedName, String reason,
                               Status status, long createdAt) {}
}
