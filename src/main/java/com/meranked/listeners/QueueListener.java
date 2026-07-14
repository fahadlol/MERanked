package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class QueueListener implements Listener {

    private final ServiceRegistry services;

    public QueueListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onKitEditorItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null) return;
        if (!event.getItem().getType().name().equals("NETHER_STAR")) return;
        if (!services.kitEditor().createEditorItem().getItemMeta().displayName()
                .equals(event.getItem().getItemMeta().displayName())) return;
        event.setCancelled(true);
        services.kitEditor().enterEditorAsync(event.getPlayer(), "Mace");
    }
}
