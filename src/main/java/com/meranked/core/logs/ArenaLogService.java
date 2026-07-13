package com.meranked.core.logs;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class ArenaLogService {

    private final MerankedLoggerService logger;
    private final String serverId;

    public ArenaLogService(MerankedLoggerService logger, String serverId) {
        this.logger = logger;
        this.serverId = serverId;
    }

    public void logArenaCreated(String arenaName, Player staff) {
        logStaff("ARENA_CREATED", arenaName, staff, Map.of("arena", arenaName));
    }

    public void logArenaRemoved(String arenaName, Player staff) {
        logStaff("ARENA_REMOVED", arenaName, staff, Map.of("arena", arenaName));
    }

    public void logArenaDisabled(String arenaName, String reason) {
        logger.log(LogCategory.ARENA, "ARENA_DISABLED", LogSeverity.MEDIUM,
                "Arena " + arenaName + " disabled: " + reason);
    }

    public void logArenaReset(String arenaName, boolean success, long durationMs) {
        logger.log(MerankedLogEvent.builder(serverId, LogCategory.ARENA,
                        success ? "ARENA_RESET_COMPLETE" : "ARENA_RESET_FAILED",
                        success ? LogSeverity.INFO : LogSeverity.HIGH,
                        "Arena " + arenaName + " reset " + (success ? "completed" : "failed"))
                .put("arena", arenaName)
                .put("durationMs", durationMs)
                .put("success", success)
                .build());
    }

    public void logArenaSelected(String arenaName, String matchId, String gamemode) {
        logger.log(MerankedLogEvent.builder(serverId, LogCategory.ARENA, "ARENA_SELECTED", LogSeverity.INFO,
                        "Arena " + arenaName + " selected for match " + matchId)
                .put("arena", arenaName)
                .put("matchId", matchId)
                .put("gamemode", gamemode)
                .build());
    }

    private void logStaff(String type, String arenaName, Player staff, Map<String, Object> data) {
        logger.logStaff(LogCategory.ARENA, type, LogSeverity.INFO,
                "Arena " + arenaName + " — " + type,
                staff.getName(), staff.getUniqueId(), data);
    }
}
