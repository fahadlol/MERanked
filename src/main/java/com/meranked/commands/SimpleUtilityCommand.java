package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SimpleUtilityCommand implements CommandExecutor {

    private final ServiceRegistry services;
    private final String type;

    public SimpleUtilityCommand(ServiceRegistry services, String type) {
        this.services = services;
        this.type = type;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        switch (type) {
            case "discord" -> services.utility().showDiscord(player);
            case "rules" -> services.utility().showRules(player);
            case "help" -> services.utility().showHelp(player);
        }
        return true;
    }
}
