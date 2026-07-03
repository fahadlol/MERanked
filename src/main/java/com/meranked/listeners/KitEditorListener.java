package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public final class KitEditorListener implements Listener {

    private final ServiceRegistry services;

    public KitEditorListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (services.kitEditor().isEditing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onPickup(PlayerPickupItemEvent event) {
        if (services.kitEditor().isEditing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && services.kitEditor().isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("meranked-action-")) {
                event.setCancelled(true);
                services.kitEditor().handleControlClick(event.getPlayer(), tag.replace("meranked-action-", ""));
                return;
            }
        }
    }
}
