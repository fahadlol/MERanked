package com.meranked.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.api.ApiRateLimiter;
import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.model.RankedMatch;
import com.meranked.rating.LeaderboardService;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WebsiteApiService extends NanoHTTPD {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final ServiceRegistry services;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean running;
    private ApiRateLimiter rateLimiter;

    public WebsiteApiService(MERankedPlugin plugin, ConfigService configService, ServiceRegistry services) {
        super(configService.get("website.yml").getString("host", "0.0.0.0"),
                configService.get("website.yml").getInt("port", 8080));
        this.plugin = plugin;
        this.configService = configService;
        this.services = services;
    }

    public void start() {
        FileConfiguration config = configService.get("website.yml");
        if (!config.getBoolean("enabled", false)) return;
        if (config.getBoolean("rate-limit.enabled", true)) {
            int rpm = config.getInt("rate-limit.requests-per-minute", 120);
            rateLimiter = new ApiRateLimiter(rpm, 60_000L);
        } else {
            rateLimiter = null;
        }
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            running = true;
            plugin.getLogger().info("Website + API started on port " + getListeningPort());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to start Website API: " + ex.getMessage());
        }
    }

    public void stop() {
        if (running) {
            super.stop();
            running = false;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = URLDecoder.decode(session.getUri(), StandardCharsets.UTF_8);
        FileConfiguration config = configService.get("website.yml");

        if (uri.startsWith("/api/") && rateLimiter != null) {
            String clientKey = session.getRemoteIpAddress();
            if (clientKey == null || clientKey.isBlank()) clientKey = "unknown";
            if (!rateLimiter.tryAcquire(clientKey)) {
                return json(Response.Status.TOO_MANY_REQUESTS, Map.of("error", "Rate limit exceeded"));
            }
        }

        if (config.getBoolean("serve-website", true) && !uri.startsWith("/api/")) {
            Response staticResponse = serveStatic(uri);
            if (staticResponse != null) return staticResponse;
        }

        String apiKey = config.getString("api-key", "");
        String provided = session.getHeaders().get("x-api-key");
        if (uri.startsWith("/api/admin/") && (provided == null || !provided.equals(apiKey))) {
            return json(Response.Status.UNAUTHORIZED, Map.of("error", "Invalid API key"));
        }

        if (uri.equals("/api/status")) {
            return json(Response.Status.OK, Map.of(
                    "online", Bukkit.getOnlinePlayers().size(),
                    "liveMatches", services.matches().liveMatches().size(),
                    "queues", services.queue().allQueues().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()))
            ));
        }
        if (uri.equals("/api/season")) {
            return json(Response.Status.OK, Map.of(
                    "id", services.seasons().currentSeasonId(),
                    "name", services.seasons().currentSeasonName()
            ));
        }
        if (uri.equals("/api/top10")) {
            String mode = services.profiles().enabledGamemodes().get(0);
            return json(Response.Status.OK, services.leaderboard().getTop(mode, 10));
        }
        if (uri.equals("/api/matches/live")) return json(Response.Status.OK, liveMatches());
        if (uri.equals("/api/regions")) return json(Response.Status.OK, services.regions().availableTags());
        if (uri.startsWith("/api/leaderboard/")) return handleLeaderboard(uri);
        if (uri.startsWith("/api/player/name/")) {
            String name = uri.substring("/api/player/name/".length());
            var off = Bukkit.getOfflinePlayer(name);
            return json(Response.Status.OK, Map.of("uuid", off.getUniqueId().toString(), "name", name));
        }
        if (uri.matches("/api/player/[0-9a-fA-F\\-]{36}/matches")) {
            String uuid = uri.split("/")[3];
            return handlePlayerMatches(uuid);
        }
        if (uri.matches("/api/player/[0-9a-fA-F\\-]{36}")) {
            String uuid = uri.substring("/api/player/".length());
            return handlePlayerProfile(uuid);
        }
        if (uri.startsWith("/api/match/")) return handleMatch(uri.substring("/api/match/".length()));
        if (uri.startsWith("/api/replay/")) return handleReplay(uri.substring("/api/replay/".length()));
        return json(Response.Status.NOT_FOUND, Map.of("error", "Unknown endpoint"));
    }

    private Response handlePlayerProfile(String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (Exception ex) {
            return json(Response.Status.BAD_REQUEST, Map.of("error", "Invalid UUID"));
        }
        var rp = services.profiles().getPlayer(uuid);
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (String mode : services.profiles().enabledGamemodes()) {
            var p = services.profiles().getProfile(uuid, mode);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gamemode", mode);
            m.put("rating", Math.round(p.rating()));
            m.put("tier", p.tier());
            m.put("peakTier", p.peakTier());
            m.put("wins", p.wins());
            m.put("losses", p.losses());
            m.put("winStreak", p.winStreak());
            m.put("confidence", services.tiers().getConfidenceLabel(p.ratingDeviation()));
            m.put("ranked", p.ranked());
            profiles.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", uuidStr);
        out.put("username", rp == null ? uuidStr : rp.username());
        out.put("region", rp == null || rp.regionHidden() ? "" : rp.region());
        out.put("profiles", profiles);
        return json(Response.Status.OK, out);
    }

    private Response handlePlayerMatches(String uuidStr) {
        List<Map<String, Object>> matches = services.database().queryAsync(conn -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (var ps = conn.prepareStatement("""
                SELECT match_id, gamemode, winner, loser, ended_at FROM ranked_matches
                WHERE winner = ? OR loser = ? ORDER BY ended_at DESC LIMIT 25
                """)) {
                ps.setString(1, uuidStr);
                ps.setString(2, uuidStr);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("matchId", rs.getString("match_id"));
                        m.put("gamemode", rs.getString("gamemode"));
                        m.put("won", uuidStr.equals(rs.getString("winner")));
                        m.put("endedAt", rs.getLong("ended_at"));
                        list.add(m);
                    }
                }
            }
            return list;
        }).join();
        return json(Response.Status.OK, Map.of("uuid", uuidStr, "matches", matches));
    }

    private Response handleMatch(String matchId) {
        List<Map<String, Object>> participants = services.database().queryAsync(conn -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (var ps = conn.prepareStatement("""
                SELECT uuid, rating_before, rating_after, tier_before, tier_after, ping
                FROM ranked_match_participants WHERE match_id = ?
                """)) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        String uuid = rs.getString("uuid");
                        m.put("uuid", uuid);
                        m.put("name", Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName());
                        m.put("ratingBefore", Math.round(rs.getDouble("rating_before")));
                        m.put("ratingAfter", Math.round(rs.getDouble("rating_after")));
                        m.put("tierBefore", rs.getString("tier_before"));
                        m.put("tierAfter", rs.getString("tier_after"));
                        m.put("ping", rs.getInt("ping"));
                        list.add(m);
                    }
                }
            }
            return list;
        }).join();
        if (participants.isEmpty()) return json(Response.Status.NOT_FOUND, Map.of("error", "Match not found"));
        return json(Response.Status.OK, Map.of("matchId", matchId, "participants", participants));
    }

    private Response handleReplay(String matchId) {
        Map<String, Object> data = services.replays().loadReplay(matchId);
        if (data == null) return json(Response.Status.NOT_FOUND, Map.of("error", "Replay not found"));
        var timeline = services.replays().loadTimeline(matchId).stream()
                .map(e -> Map.of("t", e.timestamp(), "type", e.eventType(), "desc", e.description()))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>(data);
        out.put("timeline", timeline);
        return json(Response.Status.OK, out);
    }

    private Response handleLeaderboard(String uri) {
        String[] parts = uri.split("/");
        if (parts.length < 4) return json(Response.Status.BAD_REQUEST, Map.of("error", "Invalid path"));
        String gamemode = parts[3];
        String region = parts.length >= 5 ? parts[4] : null;
        List<LeaderboardService.LeaderboardEntry> entries = region == null
                ? services.leaderboard().getTop(gamemode, 50)
                : services.leaderboard().getTop(gamemode, region, 50);
        return json(Response.Status.OK, Map.of("gamemode", gamemode, "entries", entries));
    }

    private Response serveStatic(String uri) {
        if (uri.equals("/")) uri = "/index.html";
        String path = "website" + uri;
        if (path.contains("..")) return null;
        InputStream stream = plugin.getResource(path);
        if (stream == null) return null;
        String mime = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "application/javascript" : "text/html";
        return newFixedLengthResponse(Response.Status.OK, mime, stream, -1);
    }

    private List<Map<String, Object>> liveMatches() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RankedMatch match : services.matches().liveMatches()) {
            list.add(Map.of(
                    "matchId", match.matchId(),
                    "gamemode", match.gamemode(),
                    "arena", match.arenaName() == null ? "" : match.arenaName(),
                    "state", match.state().name()
            ));
        }
        return list;
    }

    private Response json(Response.IStatus status, Object data) {
        Response response = newFixedLengthResponse(status, "application/json", gson.toJson(data));
        if (configService.get("website.yml").getBoolean("cors-enabled", true)) {
            response.addHeader("Access-Control-Allow-Origin", "*");
        }
        return response;
    }
}
