package com.meranked.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.lobby.LobbyItemType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class LobbyItemListener implements Listener {

    private final ServiceRegistry services;

    public LobbyItemListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onLobbyItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Optional<LobbyItemType> type = services.lobbyItems().identify(event.getItem());
        if (type.isEmpty()) return;

        event.setCancelled(true);
        if (!services.lobbyItems().shouldReceiveLobbyItems(event.getPlayer())) return;
        services.lobbyItems().handleUse(event.getPlayer(), type.get());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (services.lobbyItems().identify(event.getItemDrop().getItemStack()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
