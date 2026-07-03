package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class KitEditorCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public KitEditorCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        String gamemode = args.length > 0
                ? args[0].substring(0, 1).toUpperCase() + args[0].substring(1).toLowerCase()
                : "Mace";
        if (args.length > 1 && args[0].equalsIgnoreCase("editor")) {
            gamemode = args[1].substring(0, 1).toUpperCase() + args[1].substring(1).toLowerCase();
        }
        services.kitEditor().enterEditor(player, gamemode);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String mode : services.profiles().enabledGamemodes()) {
                if (mode.toLowerCase().startsWith(args[0].toLowerCase())) result.add(mode);
            }
            return result;
        }
        return List.of();
    }
}
