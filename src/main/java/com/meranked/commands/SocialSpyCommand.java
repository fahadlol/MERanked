package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SocialSpyCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public SocialSpyCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("meranked.staff.socialspy")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        services.utility().toggleSocialSpy(player);
        return true;
    }
}
