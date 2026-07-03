package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class CommandBlockListener implements Listener {

    private final ServiceRegistry services;

    public CommandBlockListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!services.utility().isCommandBlocked(player)) return;

        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        var allowed = services.config().get("config.yml").getStringList("match.allowed-commands");
        if (!allowed.contains(cmd)) {
            event.setCancelled(true);
            services.messages().send(player, "general.command-blocked-in-match");
        }
    }
}
