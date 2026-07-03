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

public final class RegionCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public RegionCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§6Region: §f" + services.regions().displayRegion(player));
            return true;
        }
        if (args[0].equalsIgnoreCase("hide")) {
            services.regions().hideRegion(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
            services.regions().setRegion(player, args[1]);
            return true;
        }
        services.regions().setRegion(player, args[0]);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String tag : services.regions().availableTags()) {
                if (tag.toLowerCase().startsWith(args[0].toLowerCase())) result.add(tag);
            }
            result.add("hide");
            return result;
        }
        return List.of();
    }
}
