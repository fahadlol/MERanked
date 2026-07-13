package com.meranked.core.staff;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class StaffStatusCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public StaffStatusCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.staff.status")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var bridge = services.discordBridge();
        sender.sendMessage("§6Staff Status");
        sender.sendMessage("§7Bridge: §f" + (bridge.isConnected() ? "§aConnected" : "§cDisconnected"));
        sender.sendMessage("§7Queued events: §f" + bridge.queueSize());
        sender.sendMessage("§7Server ID: §f" + bridge.config().serverId());
        if (sender instanceof Player player) {
            sender.sendMessage("§7Your duty: §f" + (services.staffDuty().isOnDuty(player.getUniqueId()) ? "§aOn" : "§7Off"));
        }
        sender.sendMessage("§7Staff on duty: §f" + services.staffDuty().onDutyCount());
        for (UUID uuid : services.staffDuty().onDutyStaff()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            sender.sendMessage(" §8- §f" + (name == null ? uuid.toString().substring(0, 8) : name));
        }
        return true;
    }
}
