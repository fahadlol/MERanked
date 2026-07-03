package com.meranked.staff;

import com.google.gson.Gson;
import com.meranked.MERankedPlugin;
import com.meranked.alerts.AlertService;
import com.meranked.database.DatabaseService;
import com.meranked.matches.MatchService;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import com.meranked.rating.ProfileService;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RollbackService {

    private final MERankedPlugin plugin;
    private final DatabaseService database;
    private final ProfileService profileService;
    private final MatchService matchService;
    private final AlertService alertService;
    private final Gson gson = new Gson();

    public RollbackService(MERankedPlugin plugin, DatabaseService database, ProfileService profileService,
                           MatchService matchService, AlertService alertService) {
        this.plugin = plugin;
        this.database = database;
        this.profileService = profileService;
        this.matchService = matchService;
        this.alertService = alertService;
    }

    public boolean rollbackMatch(Player staff, String matchId, String reason) {
        Optional<RankedMatch> live = matchService.getMatchById(matchId);
        if (live.isPresent()) return false;

        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT mp.uuid, mp.rating_before, mp.tier_before, m.gamemode
                FROM ranked_match_participants mp
                JOIN ranked_matches m ON m.match_id = mp.match_id
                WHERE mp.match_id = ?
                """)) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    Map<String, Object> oldValues = new java.util.HashMap<>();
                    Map<String, Object> newValues = new java.util.HashMap<>();
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String gamemode = rs.getString("gamemode");
                        double ratingBefore = rs.getDouble("rating_before");
                        String tierBefore = rs.getString("tier_before");
                        RankedProfile profile = profileService.getProfile(uuid, gamemode);
                        oldValues.put(uuid + ":" + gamemode, Map.of("rating", profile.rating(), "tier", profile.tier()));
                        profile.setRating(ratingBefore);
                        profile.setTier(tierBefore);
                        profileService.queueSave(profile);
                        newValues.put(uuid + ":" + gamemode, Map.of("rating", ratingBefore, "tier", tierBefore));
                    }
                    logRollback(staff.getUniqueId(), reason, matchId, oldValues, newValues);
                    alertService.resolveAlert(matchId);
                    return true;
                }
            }
        }).join();
    }

    /** Roll back the most recent {@code count} finished matches involving a player. */
    public int rollbackPlayerMatches(Player staff, UUID target, int count, String reason) {
        java.util.List<String> ids = database.queryAsync(conn -> {
            java.util.List<String> result = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT match_id FROM ranked_matches
                WHERE (winner = ? OR loser = ?)
                ORDER BY ended_at DESC LIMIT ?
                """)) {
                ps.setString(1, target.toString());
                ps.setString(2, target.toString());
                ps.setInt(3, count);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) result.add(rs.getString("match_id"));
                }
            }
            return result;
        }).join();
        int done = 0;
        for (String id : ids) {
            if (rollbackMatch(staff, id, reason)) done++;
        }
        return done;
    }

    /** Roll back the most recent {@code count} finished matches between two players. */
    public int rollbackBetween(Player staff, UUID a, UUID b, int count, String reason) {
        java.util.List<String> ids = database.queryAsync(conn -> {
            java.util.List<String> result = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT match_id FROM ranked_matches
                WHERE (winner = ? AND loser = ?) OR (winner = ? AND loser = ?)
                ORDER BY ended_at DESC LIMIT ?
                """)) {
                ps.setString(1, a.toString());
                ps.setString(2, b.toString());
                ps.setString(3, b.toString());
                ps.setString(4, a.toString());
                ps.setInt(5, count);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) result.add(rs.getString("match_id"));
                }
            }
            return result;
        }).join();
        int done = 0;
        for (String id : ids) {
            if (rollbackMatch(staff, id, reason)) done++;
        }
        return done;
    }

    private void logRollback(UUID staff, String reason, String matchId, Map<String, Object> oldValues, Map<String, Object> newValues) {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_rollbacks (staff_uuid, reason, match_ids, old_values, new_values, created_at)
                VALUES (?,?,?,?,?,?)
                """)) {
                ps.setString(1, staff.toString());
                ps.setString(2, reason);
                ps.setString(3, matchId);
                ps.setString(4, gson.toJson(oldValues));
                ps.setString(5, gson.toJson(newValues));
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }
}
