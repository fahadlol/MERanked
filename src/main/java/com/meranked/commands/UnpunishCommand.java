package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class UnpunishCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public UnpunishCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.unpunish")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /unpunish <punishmentId>");
            return true;
        }
        UUID staff = sender instanceof Player p ? p.getUniqueId() : null;
        services.punishments().unpunish(args[0], staff);
        sender.sendMessage("§aPunishment revoked.");
        return true;
    }
}
