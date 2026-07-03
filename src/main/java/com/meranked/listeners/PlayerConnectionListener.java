package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final ServiceRegistry services;

    public PlayerConnectionListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        services.profiles().ensurePlayer(event.getPlayer());
        event.getPlayer().getInventory().setItem(8, services.kitEditor().createEditorItem());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (services.kitEditor().isEditing(event.getPlayer().getUniqueId())) {
            services.kitEditor().leaveEditor(event.getPlayer());
        }
        if (services.queue().isQueued(event.getPlayer().getUniqueId())) {
            services.queue().leaveQueue(event.getPlayer().getUniqueId());
        }
        services.matches().handleDisconnect(event.getPlayer());
        services.matches().handleLeaveDuringMatch(event.getPlayer());
    }
}
