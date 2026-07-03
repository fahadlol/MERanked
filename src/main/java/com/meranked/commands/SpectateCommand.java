package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SpectateCommand implements CommandExecutor {

    private final ServiceRegistry services;
    private final boolean staff;

    public SpectateCommand(ServiceRegistry services, boolean staff) {
        this.services = services;
        this.staff = staff;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (staff && !player.hasPermission("meranked.staff.spectate")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) return false;
        String matchId = args.length >= 2 && args[0].equalsIgnoreCase("match") ? args[1] : args[0];
        if (args[0].equalsIgnoreCase("match") && args.length < 2) return false;

        // If player name provided, find their match
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            var match = services.matches().getMatch(target.getUniqueId());
            if (match.isPresent()) matchId = match.get().matchId();
        }
        services.spectating().spectateMatch(player, matchId, staff);
        return true;
    }
}
