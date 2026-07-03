package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SettingsCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public SettingsCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        services.gui().openSettings(player);
        return true;
    }
}
