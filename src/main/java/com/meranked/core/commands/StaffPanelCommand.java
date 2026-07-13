package com.meranked.core.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StaffPanelCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public StaffPanelCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("meranked.staff.panel")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        services.gui().openStaffPanel(player);
        return true;
    }
}
