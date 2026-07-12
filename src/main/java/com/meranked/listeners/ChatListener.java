package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.gui.GuiManager;
import com.meranked.util.DurationUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Enforces chat mutes and handles staff punishment duration chat input.
 */
public final class ChatListener implements Listener {

    private final ServiceRegistry services;

    public ChatListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPunishDurationInput(AsyncChatEvent event) {
        if (!services.gui().hasPendingPunishDuration(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String raw = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        if (raw.equalsIgnoreCase("cancel")) {
            services.gui().cancelPendingPunishDuration(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage("§7Punishment input cancelled.");
            return;
        }
        if (!DurationUtil.isDuration(raw)) {
            event.getPlayer().sendMessage("§cInvalid duration. Examples: 7d, 12h, 30m, permanent");
            return;
        }
        GuiManager.PendingPunishInput pending = services.gui().pollPendingPunishDuration(event.getPlayer().getUniqueId());
        if (pending == null) return;
        long seconds = DurationUtil.parseSeconds(raw);
        String label = seconds <= 0 ? "Permanent" : raw;
        services.gui().openPunishConfirm(event.getPlayer(), pending.target(), pending.targetName(),
                pending.type(), pending.reason(), seconds, label);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (services.gui().hasPendingPunishDuration(event.getPlayer().getUniqueId())) return;
        if (services.punishments().isMuted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            FileConfiguration cfg = services.config().get("punishments.yml");
            String msg = cfg.getString("punishments.messages.muted",
                    "<prefix> <red>You have been muted: <reason></red>").replace("<reason>", "Chat mute");
            event.getPlayer().sendMessage(services.messages().format(msg));
        }
    }
}
