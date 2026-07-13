package com.meranked.core.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import com.meranked.core.logs.MerankedLogEvent;
import com.meranked.core.logs.MerankedLoggerService;
import com.meranked.model.RankedMatch;
import com.meranked.staff.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DiscordRequestHandler {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final MerankedLoggerService logger;
    private final DiscordBridgeManager bridge;

    public DiscordRequestHandler(MERankedPlugin plugin, ServiceRegistry services,
                                 MerankedLoggerService logger, DiscordBridgeManager bridge) {
        this.plugin = plugin;
        this.services = services;
        this.logger = logger;
        this.bridge = bridge;
    }

    public void handle(String rawJson, ResponseSender sender) {
        try {
            JsonObject req = JsonParser.parseString(rawJson).getAsJsonObject();
            String requestId = req.has("requestId") ? req.get("requestId").getAsString() : UUID.randomUUID().toString();
            String type = req.get("type").getAsString();

            logger.log(MerankedLogEvent.builder(bridge.config().serverId(), LogCategory.DISCORD, type,
                            LogSeverity.INFO, "Discord bot request: " + type)
                    .put("requestId", requestId)
                    .build());

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("type", type.replace("_REQUEST", "_RESPONSE"));

            switch (type) {
                case "STAFF_STATUS_REQUEST" -> handleStaffStatus(response);
                case "PLAYER_LOOKUP_REQUEST" -> handlePlayerLookup(req, response);
                case "PUNISHMENT_LOOKUP_REQUEST" -> handlePunishmentLookup(req, response);
                case "REPORT_LOOKUP_REQUEST" -> handleReportLookup(req, response);
                case "MATCH_HISTORY_REQUEST" -> handleMatchHistory(req, response);
                case "SUSPICION_LOOKUP_REQUEST" -> handleSuspicionLookup(req, response);
                case "TICKET_PLAYER_LOOKUP" -> handleTicketLookup(req, response);
                case "STAFF_PING" -> {
                    response.addProperty("success", true);
                    response.addProperty("message", "MERanked bridge online");
                }
                case "BROADCAST_STAFF_MESSAGE" -> {
                    String message = req.has("message") ? req.get("message").getAsString() : "";
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("meranked.staff")) {
                            p.sendMessage("§6[Staff] §f" + BridgeTextUtil.sanitize(message));
                        }
                    }
                    response.addProperty("success", true);
                }
                default -> {
                    response.addProperty("success", false);
                    response.addProperty("error", "Unknown request type");
                }
            }

            if (!response.has("success")) {
                response.addProperty("success", true);
            }
            sender.send(response.toString());
        } catch (Exception ex) {
            JsonObject err = new JsonObject();
            err.addProperty("success", false);
            err.addProperty("error", ex.getMessage());
            sender.send(err.toString());
        }
    }

    private void handleStaffStatus(JsonObject response) {
        JsonObject data = new JsonObject();
        data.addProperty("bridgeConnected", bridge.isConnected());
        data.addProperty("queuedEvents", bridge.queueSize());
        data.addProperty("serverId", bridge.config().serverId());
        data.addProperty("staffOnDuty", services.staffDuty().onDutyCount());
        response.add("data", data);
    }

    private void handlePlayerLookup(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        JsonObject player = buildPlayerSummary(offline);
        response.add("player", player);
    }

    private void handlePunishmentLookup(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        List<PunishmentService.Punishment> history = services.punishments().history(offline.getUniqueId());
        JsonObject summary = new JsonObject();
        summary.addProperty("activeCount", history.stream().filter(PunishmentService.Punishment::isActive).count());
        summary.addProperty("totalCount", history.size());
        response.add("punishments", summary);
        response.add("player", buildPlayerSummary(offline));
    }

    private void handleReportLookup(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        response.add("reports", services.reports().summaryForPlayer(offline.getUniqueId()));
        response.add("player", buildPlayerSummary(offline));
    }

    private void handleMatchHistory(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        response.addProperty("liveMatch",
                services.matches().getMatch(offline.getUniqueId()).map(RankedMatch::matchId).orElse(null));
        response.addProperty("liveMatches", services.matches().liveMatches().size());
    }

    private void handleSuspicionLookup(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        response.addProperty("suspicionScore", services.suspicion().getScore(offline.getUniqueId()));
    }

    private void handleTicketLookup(JsonObject req, JsonObject response) {
        String name = req.get("player").getAsString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        JsonObject ticket = new JsonObject();
        ticket.add("player", buildPlayerSummary(offline));
        ticket.addProperty("suspicionScore", services.suspicion().getScore(offline.getUniqueId()));
        ticket.add("reports", services.reports().summaryForPlayer(offline.getUniqueId()));
        List<PunishmentService.Punishment> history = services.punishments().history(offline.getUniqueId());
        ticket.addProperty("activePunishments",
                history.stream().filter(PunishmentService.Punishment::isActive).count());
        ticket.addProperty("staffNotesCount",
                services.staffNotes().getNotes("PLAYER", offline.getUniqueId().toString()).size());
        response.add("ticketEvidence", ticket);
    }

    private JsonObject buildPlayerSummary(OfflinePlayer offline) {
        JsonObject player = new JsonObject();
        player.addProperty("name", offline.getName() == null ? "Unknown" : offline.getName());
        player.addProperty("uuid", offline.getUniqueId().toString());
        player.addProperty("online", offline.isOnline());
        player.addProperty("lastSeen", offline.getLastSeen());
        if (offline.getFirstPlayed() > 0) player.addProperty("firstJoined", offline.getFirstPlayed());
        if (offline.isOnline() && offline.getPlayer() != null) {
            player.addProperty("currentWorld", offline.getPlayer().getWorld().getName());
        }
        var rp = services.profiles().getPlayer(offline.getUniqueId());
        if (rp != null) {
            player.addProperty("region", rp.regionHidden() ? "Hidden" : rp.region());
        }
        var modes = services.profiles().enabledGamemodes();
        if (!modes.isEmpty()) {
            var profile = services.profiles().getProfile(offline.getUniqueId(), modes.get(0));
            player.addProperty("rank", profile.tier());
        }
        return player;
    }

    @FunctionalInterface
    public interface ResponseSender {
        void send(String json);
    }
}
