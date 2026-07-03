package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

public final class CombatListener implements Listener {

    private final ServiceRegistry services;

    public CombatListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Optional<RankedMatch> match = services.matches().getMatch(damager.getUniqueId());
        if (match.isEmpty() || match.get().state() != RankedMatch.State.ACTIVE) return;
        if (!match.get().matchId().equals(services.matches().getMatch(victim.getUniqueId()).map(RankedMatch::matchId).orElse(""))) {
            event.setCancelled(true);
            return;
        }

        match.get().stats(damager.getUniqueId()).addDamage(event.getFinalDamage());
        match.get().stats(damager.getUniqueId()).incrementHits();
        services.replays().recordCombatEvent(match.get(), "DAMAGE_DEALT",
                com.meranked.util.TextUtil.formatMatchTime(match.get().durationMillis()) + " - "
                        + damager.getName() + " dealt " + String.format("%.1f", event.getFinalDamage()) + " damage");
    }

    @EventHandler
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        services.matches().getMatch(player.getUniqueId()).ifPresent(match -> {
            match.stats(match.opponent(player.getUniqueId())).incrementTotems();
            services.replays().recordCombatEvent(match, "TOTEM_POP",
                    com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()) + " - totem popped");
        });
    }

    @EventHandler
    public void onCrystalPlace(BlockPlaceEvent event) {
        if (!event.getBlock().getType().name().contains("CRYSTAL")) return;
        services.matches().getMatch(event.getPlayer().getUniqueId()).ifPresent(match -> {
            match.stats(event.getPlayer().getUniqueId()).incrementCrystals();
            services.replays().recordCombatEvent(match, "CRYSTAL_PLACED",
                    com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()) + " - crystal placed");
        });
    }
}
