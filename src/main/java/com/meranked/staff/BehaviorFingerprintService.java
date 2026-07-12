package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.AlertSeverity;
import com.meranked.model.RankedMatch;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-match behavioral signals (CPS, damage rate) and flags smurf-like performance on new accounts.
 */
public final class BehaviorFingerprintService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public BehaviorFingerprintService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public boolean enabled() {
        return services.config().get("behavior-fingerprint.yml").getBoolean("behavior-fingerprint.enabled", true);
    }

    public void startMatch(UUID uuid, String matchId) {
        if (!enabled()) return;
        sessions.put(uuid, new Session(matchId));
    }

    public void recordAttack(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s != null) s.attacks++;
    }

    public void recordMove(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s != null) s.moveSamples++;
    }

    public void endMatch(UUID uuid, RankedMatch match) {
        if (!enabled()) return;
        Session s = sessions.remove(uuid);
        if (s == null) return;

        long durationSec = Math.max(1, match.durationMillis() / 1000);
        double cps = (double) s.attacks / durationSec;
        var stats = match.stats(uuid);
        double dmgPerSec = stats.damageDealt() / durationSec;

        updateBaseline(uuid, match.gamemode(), cps, dmgPerSec);
        evaluateSmurf(uuid, match.gamemode(), cps, dmgPerSec, stats.damageDealt());
    }

    private void evaluateSmurf(UUID uuid, String gamemode, double cps, double dmgPerSec, double totalDmg) {
        FileConfiguration cfg = services.config().get("behavior-fingerprint.yml");
        double cpsThreshold = cfg.getDouble("behavior-fingerprint.smurf-cps-threshold", 14);
        var player = services.profiles().getPlayer(uuid);
        if (player == null) return;
        long accountAgeDays = (System.currentTimeMillis() - player.createdAt()) / 86400000L;
        boolean suspicious = accountAgeDays <= 7 || player.suspicionScore() >= 30;

        if (!suspicious) return;
        if (cps < cpsThreshold && dmgPerSec < 12) return;

        int increase = cfg.getInt("behavior-fingerprint.suspicion-increase", 12);
        services.suspicion().addScore(uuid, increase, "High mechanical performance on new/suspicious account");

        AlertSeverity sev;
        try {
            sev = AlertSeverity.valueOf(cfg.getString("behavior-fingerprint.alert-severity", "MEDIUM").toUpperCase());
        } catch (IllegalArgumentException ex) {
            sev = AlertSeverity.MEDIUM;
        }
        services.alerts().createAlert("BEHAVIOR_SMURF", sev,
                String.format("CPS %.1f, DPS %.1f in %s (account age %dd)", cps, dmgPerSec, gamemode, accountAgeDays),
                null, java.util.List.of(uuid));
    }

    private void updateBaseline(UUID uuid, String gamemode, double cps, double dmgPerSec) {
        if (!services.config().get("behavior-fingerprint.yml").getBoolean("behavior-fingerprint.store-rolling-average", true)) {
            return;
        }
        services.database().executeAsync(conn -> {
            double avgCps = cps;
            double avgDps = dmgPerSec;
            int samples = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT avg_cps, avg_dps, samples FROM ranked_behavior_fingerprints WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int n = rs.getInt("samples");
                        avgCps = (rs.getDouble("avg_cps") * n + cps) / (n + 1);
                        avgDps = (rs.getDouble("avg_dps") * n + dmgPerSec) / (n + 1);
                        samples = n + 1;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(services.database().sql("""
                INSERT OR REPLACE INTO ranked_behavior_fingerprints (uuid, gamemode, avg_cps, avg_dps, samples, updated_at)
                VALUES (?,?,?,?,?,?)
                """))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                ps.setDouble(3, avgCps);
                ps.setDouble(4, avgDps);
                ps.setInt(5, samples);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public void clear(UUID uuid) {
        sessions.remove(uuid);
    }

    private static final class Session {
        final String matchId;
        int attacks;
        int moveSamples;
        Session(String matchId) { this.matchId = matchId; }
    }
}
