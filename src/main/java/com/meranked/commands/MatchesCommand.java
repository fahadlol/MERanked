package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedMatch;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class MatchesCommand implements CommandExecutor {

    private final ServiceRegistry services;

    public MatchesCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sender.sendMessage("§6Live Ranked Matches");
        for (RankedMatch match : services.matches().liveMatches()) {
            sender.sendMessage("§7- §f" + match.matchId() + " §8| §e" + match.gamemode() + " §8| §a" + match.state());
        }
        return true;
    }
}
