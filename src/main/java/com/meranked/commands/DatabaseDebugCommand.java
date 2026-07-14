package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Development helper exposing async database/cache metrics.
 */
public final class DatabaseDebugCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public DatabaseDebugCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.admin.debug")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        var db = services.database();
        sender.sendMessage("§6MERanked database metrics");
        sender.sendMessage("§7Accepting ops: §f" + db.isAcceptingOperations());
        sender.sendMessage("§7Active DB ops: §f" + db.activeOperations());
        sender.sendMessage("§7Queued DB ops: §f" + db.queuedOperations());
        sender.sendMessage("§7Hikari active/idle/waiting: §f" + db.hikariActiveConnections()
                + "§7/§f" + db.hikariIdleConnections() + "§7/§f" + db.hikariWaitingThreads());
        sender.sendMessage("§7Cached profiles: §f" + services.profiles().cachedProfileCount()
                + " §7(in-flight: §f" + services.profiles().inflightProfileLoads() + "§7)");
        sender.sendMessage("§7Cached players: §f" + services.profiles().cachedPlayerCount()
                + " §7(in-flight: §f" + services.profiles().inflightPlayerLoads() + "§7)");
        sender.sendMessage("§7Cached kits: §f" + services.kits().cachedKitCount()
                + " §7(in-flight: §f" + services.kits().inflightKitLoads() + "§7)");
        return true;
    }
}
