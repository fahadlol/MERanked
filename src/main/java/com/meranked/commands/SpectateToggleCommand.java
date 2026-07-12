package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SpectateToggleCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public SpectateToggleCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        services.settings().toggle(player.getUniqueId(), "spectate-requests");
        services.gui().openSettings(player);
        return true;
    }
}
