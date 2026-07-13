package com.meranked.core.punishments;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import com.meranked.core.logs.MerankedLoggerService;
import com.meranked.staff.PunishmentService;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

public final class PunishmentLogService {

    private final ServiceRegistry services;
    private final MerankedLoggerService logger;

    public PunishmentLogService(ServiceRegistry services, MerankedLoggerService logger) {
        this.services = services;
        this.logger = logger;
    }

    public void logCreated(PunishmentService.Punishment p) {
        Map<String, Object> data = baseData(p);
        data.put("silent", false);
        logger.logStaffPlayer(LogCategory.PUNISHMENT, "PUNISHMENT_CREATED", severityFor(p.type()),
                targetName(p) + " was punished (" + p.type() + ") by " + staffNameFromUuid(p.staffUuid()),
                staffNameFromUuid(p.staffUuid()), p.staffUuid(), targetName(p), p.uuid(), data);
    }

    public void logRemoved(String punishmentId, java.util.UUID target, java.util.UUID staff, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("punishmentId", punishmentId);
        data.put("removedReason", reason);
        logger.logStaffPlayer(LogCategory.PUNISHMENT, "PUNISHMENT_REMOVED", LogSeverity.MEDIUM,
                "Punishment " + punishmentId + " removed for " + Bukkit.getOfflinePlayer(target).getName(),
                staffNameFromUuid(staff), staff, Bukkit.getOfflinePlayer(target).getName(), target, data);
    }

    public void logExpired(PunishmentService.Punishment p) {
        Map<String, Object> data = baseData(p);
        logger.logPlayer(LogCategory.PUNISHMENT, "PUNISHMENT_EXPIRED", LogSeverity.LOW,
                "Punishment " + p.punishmentId() + " expired for " + targetName(p),
                targetName(p), p.uuid());
    }

    public void logCommandFailed(String staffName, java.util.UUID staffUuid, String reason) {
        logger.logStaff(LogCategory.PUNISHMENT, "PUNISHMENT_COMMAND_FAILED", LogSeverity.LOW,
                "Punishment command failed: " + reason, staffName, staffUuid,
                Map.of("reason", reason));
    }

    private Map<String, Object> baseData(PunishmentService.Punishment p) {
        Map<String, Object> data = new HashMap<>();
        data.put("punishmentId", p.punishmentId());
        data.put("punishmentType", mapType(p.type()));
        data.put("reason", p.reason());
        data.put("duration", formatDuration(p.durationMs()));
        data.put("createdAt", p.startTime());
        data.put("expiresAt", p.endTime() == 0 ? null : p.endTime());
        data.put("active", p.isActive());
        if (p.evidenceMatchId() != null) data.put("evidenceMatchId", p.evidenceMatchId());
        return data;
    }

    private String mapType(PunishmentService.Type type) {
        return switch (type) {
            case BAN -> "BAN";
            case MUTE, CHATMUTE -> "MUTE";
            case KICK -> "KICK";
            case RANKEDBAN -> "BLACKLIST";
            case QUEUEBAN -> "QUEUEBAN";
            case SPECTATEBAN -> "SPECTATEBAN";
        };
    }

    private LogSeverity severityFor(PunishmentService.Type type) {
        return switch (type) {
            case BAN, RANKEDBAN -> LogSeverity.HIGH;
            case KICK, QUEUEBAN, SPECTATEBAN -> LogSeverity.MEDIUM;
            default -> LogSeverity.LOW;
        };
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "Permanent";
        long sec = ms / 1000;
        if (sec < 3600) return sec + "s";
        if (sec < 86400) return (sec / 3600) + "h";
        return (sec / 86400) + "d";
    }

    private String targetName(PunishmentService.Punishment p) {
        String n = Bukkit.getOfflinePlayer(p.uuid()).getName();
        return n == null ? "Unknown" : n;
    }

    private String staffNameFromUuid(java.util.UUID uuid) {
        if (uuid == null) return "Console";
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? "Staff" : n;
    }
}
