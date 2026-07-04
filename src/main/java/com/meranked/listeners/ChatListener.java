package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Enforces chat mutes (MUTE / CHATMUTE punishments) issued through the punishment system.
 */
public final class ChatListener implements Listener {

    private final ServiceRegistry services;

    public ChatListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (services.punishments().isMuted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            FileConfiguration cfg = services.config().get("punishments.yml");
            String msg = cfg.getString("punishments.messages.muted",
                    "<prefix> <red>You have been muted: <reason></red>").replace("<reason>", "Chat mute");
            event.getPlayer().sendMessage(services.messages().format(msg));
        }
    }
}
