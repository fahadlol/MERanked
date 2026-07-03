package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.AlertSeverity;
import com.meranked.model.RankedPlayer;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Lowers placement caps and raises monitoring for new / suspicious accounts.
 */
public final class AltLockService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;

    public AltLockService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public enum Trust { TRUSTED, NEW, SUSPICIOUS }

    /**
     * Evaluates account trust and applies a placement-cap override to the profile if needed.
     * Should be called when a player enters placements.
     */
    public void evaluate(Player player, RankedProfile profile) {
        FileConfiguration cfg = services.config().get("alt-lock.yml");
        if (!cfg.getBoolean("alt-confidence-lock.enabled", true)) return;
        if (profile.ranked()) return;

        Trust trust = classify(player, cfg);
        String cap;
        switch (trust) {
            case SUSPICIOUS -> cap = cfg.getString("alt-confidence-lock.caps.suspicious", "LT4");
            case NEW -> cap = cfg.getString("alt-confidence-lock.caps.new-account", "HT4");
            default -> cap = cfg.getString("alt-confidence-lock.caps.trusted", "LT3");
        }
        profile.setPlacementCapOverride(cap);

        if (trust != Trust.TRUSTED) {
            player.sendMessage(services.messages().format(cfg.getString("alt-confidence-lock.player-message", "")));
            String reason = (trust == Trust.NEW ? "New account" : "Suspicious account")
                    + ". Placement cap lowered to " + cap + ".";
            AlertSeverity sev = severity(cfg.getString("alt-confidence-lock.alert-severity", "MEDIUM"));
            services.alerts().createAlert("ALT_CONFIDENCE_LOCK", sev, reason, null,
                    java.util.List.of(player.getUniqueId()));
        }
    }

    private Trust classify(Player player, FileConfiguration cfg) {
        RankedPlayer rp = services.profiles().getPlayer(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean suspicious = false;
        boolean isNew = false;

        // Account age
        long newDays = cfg.getLong("alt-confidence-lock.new-account-days", 3);
        if (rp != null && rp.createdAt() > 0 && now - rp.createdAt() < newDays * 86400_000L) {
            isNew = true;
        }
        // Vanilla first-played age fallback
        if (now - player.getFirstPlayed() < newDays * 86400_000L) isNew = true;

        // Low playtime
        long lowHours = cfg.getLong("alt-confidence-lock.low-playtime-hours", 5);
        int playedTicks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        if (playedTicks / 20.0 / 3600.0 < lowHours) isNew = true;

        // Suspicion score
        int threshold = cfg.getInt("alt-confidence-lock.suspicious-suspicion-threshold", 50);
        if (rp != null && rp.suspicionScore() >= threshold) suspicious = true;

        // Same IP as another ranked player
        if (cfg.getBoolean("alt-confidence-lock.flag-same-ip", true) && sharesIp(player)) {
            suspicious = true;
        }

        if (suspicious) return Trust.SUSPICIOUS;
        if (isNew) return Trust.NEW;
        return Trust.TRUSTED;
    }

    private boolean sharesIp(Player player) {
        if (player.getAddress() == null) return false;
        String ip = player.getAddress().getAddress().getHostAddress();
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(player) || other.getAddress() == null) continue;
            if (ip.equals(other.getAddress().getAddress().getHostAddress())) return true;
        }
        return false;
    }

    private AlertSeverity severity(String name) {
        try {
            return AlertSeverity.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AlertSeverity.MEDIUM;
        }
    }
}
