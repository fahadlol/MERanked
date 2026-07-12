package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Optional;

/** Records additional ranked replay events beyond core combat damage. */
public final class ReplayMatchListener implements Listener {

    private final ServiceRegistry services;

    public ReplayMatchListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        services.matches().getMatch(victim.getUniqueId()).ifPresent(match -> {
            String killer = victim.getKiller() == null ? "unknown" : victim.getKiller().getName();
            services.replays().recordCombatEvent(match, "DEATH",
                    com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()) + " - "
                            + victim.getName() + " died (killer: " + killer + ")");
        });
    }

    @EventHandler
    public void onPearl(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) return;
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        services.matches().getMatch(player.getUniqueId()).ifPresent(match ->
                services.replays().recordCombatEvent(match, "PEARL_THROWN",
                        com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()) + " - pearl thrown"));
    }

    @EventHandler
    public void onGapple(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().name().contains("GOLDEN_APPLE")) {
            services.matches().getMatch(event.getPlayer().getUniqueId()).ifPresent(match ->
                    services.replays().recordCombatEvent(match, "GOLDEN_APPLE_EATEN",
                            com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()) + " - golden apple"));
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player)) return;
        Optional<RankedMatch> match = services.matches().getMatch(damager.getUniqueId());
        if (match.isEmpty()) return;
        String type = event.isCritical() ? "CRITICAL_HIT" : "HIT_LANDED";
        services.replays().recordCombatEvent(match.get(), type,
                com.meranked.util.TextUtil.formatMatchTime(match.get().durationMillis()) + " - hit on "
                        + ((Player) event.getEntity()).getName());
    }
}
