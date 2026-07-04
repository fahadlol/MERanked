package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.staff.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class HistoryCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public HistoryCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.history")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /history <player>");
            return true;
        }
        UUID target = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        sender.sendMessage("§6§lPunishment history: §e" + args[0]);
        var list = services.punishments().history(target);
        if (list.isEmpty()) {
            sender.sendMessage("§7No punishments on record.");
            return true;
        }
        for (PunishmentService.Punishment p : list) {
            String status = p.isActive() ? "§aACTIVE" : "§8expired";
            String dur = p.durationMs() <= 0 ? "Permanent" : (p.durationMs() / 1000) + "s";
            sender.sendMessage("§7[" + p.punishmentId() + "] §e" + p.type() + " §7- " + p.reason()
                    + " §8(" + dur + ") " + status);
        }
        return true;
    }
}
