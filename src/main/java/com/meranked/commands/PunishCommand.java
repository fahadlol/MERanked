package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PunishCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public PunishCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.punish")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player staff)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /punish <player>");
            return true;
        }
        services.gui().openPunishType(staff, Bukkit.getOfflinePlayer(args[0]).getUniqueId(), args[0]);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
