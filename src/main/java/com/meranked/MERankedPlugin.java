package com.meranked;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.commands.CommandRegistrar;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.listeners.ListenerRegistrar;
import com.meranked.placeholders.MERankedExpansion;
import com.meranked.util.TaskScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class MERankedPlugin extends JavaPlugin {

    private static MERankedPlugin instance;
    private ServiceRegistry services;
    private TaskScheduler scheduler;

    @Override
    public void onEnable() {
        instance = this;
        scheduler = new TaskScheduler(this);
        getLogger().info("Starting MERanked v" + getPluginMeta().getVersion() + "...");

        services = new ServiceRegistry(this);
        getLogger().info("[1/4] Loading core services...");
        services.registerCore();

        getLogger().info("[2/4] Loading configuration (" + services.config().configFileCount() + " files)...");
        services.loadConfigs();

        getLogger().info("[3/4] Initializing database and Redis...");
        services.initializeAsync().thenRun(() -> getServer().getScheduler().runTask(this, () -> {
            getLogger().info("[4/4] Registering modules, commands, and listeners...");
            services.registerModules();
            new CommandRegistrar(this, services).register();
            new ListenerRegistrar(this, services).register();
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new MERankedExpansion(services).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            }
            getLogger().info("MERanked enabled - I cooked em.");
        })).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            getLogger().severe("Failed to initialize MERanked: " + cause.getMessage());
            cause.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return null;
        });
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.shutdown();
        }
        instance = null;
        getLogger().info("MERanked disabled.");
    }

    public static MERankedPlugin getInstance() {
        return instance;
    }

    public ServiceRegistry services() {
        return services;
    }

    public TaskScheduler tasks() {
        return scheduler;
    }

    public ConfigService config() {
        return services.config();
    }
}
