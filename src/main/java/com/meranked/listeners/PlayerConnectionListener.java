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
        services.profiles().preloadAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        services.lobbyItems().giveLobbyItems(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        if (services.kitEditor().isEditing(uuid)) {
            services.kitEditor().leaveEditor(event.getPlayer());
        }
        if (services.queue().isQueued(uuid)) {
            services.queue().leaveQueue(uuid);
        }
        services.matches().handleDisconnect(event.getPlayer());
        services.scoreboards().remove(event.getPlayer());
        services.profiles().flushNow();
        services.profiles().unloadPlayer(uuid);
    }
}
