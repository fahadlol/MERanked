package com.meranked.core.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class LookupCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public LookupCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.staff.lookup")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /lookup <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        sender.sendMessage("§6Lookup: §f" + (target.getName() == null ? args[0] : target.getName()));
        sender.sendMessage("§7UUID: §f" + target.getUniqueId());
        sender.sendMessage("§7Online: §f" + target.isOnline());
        sender.sendMessage("§7Suspicion: §f" + services.suspicion().getScore(target.getUniqueId()));
        var punishments = services.punishments().history(target.getUniqueId());
        long active = punishments.stream().filter(p -> p.isActive()).count();
        sender.sendMessage("§7Punishments: §f" + punishments.size() + " (§c" + active + " active§f)");
        var modes = services.profiles().enabledGamemodes();
        if (!modes.isEmpty()) {
            var profile = services.profiles().getProfile(target.getUniqueId(), modes.get(0));
            sender.sendMessage("§7Rank: §f" + profile.tier());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
