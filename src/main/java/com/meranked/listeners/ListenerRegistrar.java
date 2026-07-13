package com.meranked.listeners;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.listeners.DiscordBridgeListener;
import org.bukkit.event.Listener;

public final class ListenerRegistrar {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;

    public ListenerRegistrar(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void register() {
        register(new PlayerConnectionListener(services));
        register(new MatchListener(services));
        register(new QueueListener(services));
        register(new KitEditorListener(services));
        register(new CombatListener(services));
        register(new CommandBlockListener(services));
        register(new ChatListener(services));
        register(new DiscordBridgeListener(services));
    }

    private void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
