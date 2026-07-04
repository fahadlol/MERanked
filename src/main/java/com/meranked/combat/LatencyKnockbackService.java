package com.meranked.combat;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Normalizes knockback during ranked matches so high-ping players don't gain a KB advantage.
 */
public final class LatencyKnockbackService implements Listener {

    private final ServiceRegistry services;

    public LatencyKnockbackService(ServiceRegistry services) {
        this.services = services;
    }

    public boolean enabled() {
        return services.config().get("knockback-sync.yml").getBoolean("knockback-sync.enabled", true);
    }

    /** Correction multiplier applied to knockback velocity (closer to 1.0 = more normalized). */
    public double correctionFactor(int pingMs) {
        if (!enabled()) return 1.0;
        FileConfiguration cfg = services.config().get("knockback-sync.yml");
        int offset = cfg.getInt("knockback-sync.ping-offset-ms", 25);
        int ref = cfg.getInt("knockback-sync.reference-ping", 50);
        double maxCorr = cfg.getDouble("knockback-sync.max-correction", 0.18);

        int adjusted = Math.max(0, pingMs - offset);
        if (adjusted <= ref) return 1.0;
        double excess = (adjusted - ref) / 200.0;
        return 1.0 - Math.min(maxCorr, excess * maxCorr);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!enabled()) return;
        FileConfiguration cfg = services.config().get("knockback-sync.yml");
        if (cfg.getBoolean("knockback-sync.ranked-matches-only", true)) {
            Optional<RankedMatch> match = services.matches().getMatch(event.getPlayer().getUniqueId());
            if (match.isEmpty() || match.get().state() != RankedMatch.State.ACTIVE) return;
        }
        double factor = correctionFactor(event.getPlayer().getPing());
        if (Math.abs(factor - 1.0) < 0.001) return;
        Vector v = event.getVelocity();
        event.setVelocity(new Vector(v.getX() * factor, v.getY() * factor, v.getZ() * factor));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        services.behaviorFingerprint().recordAttack(attacker.getUniqueId());
    }
}
