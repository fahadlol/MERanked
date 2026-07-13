package com.meranked.core.staff;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StaffDutyCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public StaffDutyCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("meranked.staff.duty")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        boolean on = services.staffDuty().toggleDuty(player);
        player.sendMessage(on ? "§aYou are now §lon duty§a." : "§7You are now §loff duty§7.");
        return true;
    }
}
