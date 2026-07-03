package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Optional;

public final class MatchListener implements Listener {

    private final ServiceRegistry services;

    public MatchListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        services.matches().handleDeath(victim, killer);
        event.setDeathMessage(null);
        event.getDrops().clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Optional<RankedMatch> match = services.matches().getMatch(player.getUniqueId());
        match.filter(m -> m.state() == RankedMatch.State.VOTING)
                .ifPresent(m -> services.arenaVoting().handleClick(event, m));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        services.matches().getMatch(event.getPlayer().getUniqueId())
                .filter(m -> m.state() == RankedMatch.State.CINEMATIC)
                .ifPresent(m -> services.cinematic().trySkip(event.getPlayer(), m));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Optional<RankedMatch> match = services.matches().getMatch(event.getPlayer().getUniqueId());
        if (match.isEmpty()) return;
        RankedMatch m = match.get();
        if (m.state() == RankedMatch.State.VOTING || m.state() == RankedMatch.State.CINEMATIC
                || m.state() == RankedMatch.State.COUNTDOWN) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }
}
