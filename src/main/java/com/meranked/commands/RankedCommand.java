package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RankedCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public RankedCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && AdminCommands.handle(services, sender, args[0].toLowerCase(), args)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.must-be-player");
            return true;
        }
        if (args.length == 0) {
            services.gui().openRankedMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "queue" -> {
                if (args.length < 2) return false;
                if (services.matches().isInMatch(player.getUniqueId())) {
                    services.messages().send(player, "queue.in-match");
                    return true;
                }
                if (services.kitEditor().isEditing(player.getUniqueId())) {
                    services.messages().send(player, "queue.in-editor");
                    return true;
                }
                String mode = resolveGamemode(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (mode == null) {
                    services.messages().sendPrefixed(player, "queue.invalid-gamemode", Map.of("gamemode", args[1]));
                    return true;
                }
                services.queue().joinQueue(player, mode);
            }
            case "leave" -> services.queue().leaveQueue(player.getUniqueId());
            case "cancel" -> handleCancel(player);
            case "card" -> {
                UUID target = player.getUniqueId();
                String name = player.getName();
                if (args.length > 1) {
                    target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                    name = args[1];
                }
                services.gui().openIdentityCard(player, target, name);
            }
            case "fairness" -> services.gui().openFairnessDashboard(player);
            case "reload" -> {
                if (!sender.hasPermission("meranked.admin")) {
                    services.messages().send(sender, "general.no-permission");
                    return true;
                }
                boolean force = args.length > 1 && args[1].equalsIgnoreCase("force");
                if (!force && (!services.matches().liveMatches().isEmpty() || anyEditing())) {
                    services.messages().send(sender, "general.reload-blocked");
                    return true;
                }
                services.config().reloadAll();
                services.messages().reload();
                services.messages().send(sender, "general.reload-success");
            }
            case "alerts" -> {
                if (!sender.hasPermission("meranked.staff")) {
                    services.messages().send(sender, "general.no-permission");
                    return true;
                }
                services.gui().openStaffAlerts(player);
            }
            case "profile" -> services.gui().openProfile(player, args.length > 1 ? resolveGamemode(args[1]) : null);
            case "leaderboard" -> services.gui().openLeaderboard(player, args.length > 1 ? resolveGamemode(args[1]) : null);
            case "dodgeinfo" -> {
                if (args.length < 2 || !sender.hasPermission("meranked.staff")) return false;
                var record = services.antiDodge().getRecord(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                services.messages().send(sender, "dodge.info", Map.of(
                        "player", args[1],
                        "count", String.valueOf(record.dodgeCount()),
                        "hidden", String.valueOf(record.hiddenUntil() > System.currentTimeMillis()),
                        "time", com.meranked.util.TextUtil.formatDuration(Math.max(0, (record.cooldownUntil() - System.currentTimeMillis()) / 1000))
                ));
            }
            case "cleardodges" -> {
                if (args.length < 2 || !sender.hasPermission("meranked.staff")) return false;
                services.antiDodge().clearDodges(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
            }
            case "rollback" -> {
                if (args.length < 2 || !sender.hasPermission("meranked.staff")) return false;
                services.rollback().rollbackMatch(player, args[1], "Staff rollback");
                services.messages().sendPrefixed(sender, "staff.rollback-success", Map.of("match_id", args[1]));
            }
            case "suspicion" -> {
                if (args.length < 2 || !sender.hasPermission("meranked.staff")) return false;
                int score = services.suspicion().getScore(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                sender.sendMessage("§6Suspicion: §f" + score + "/100");
            }
            case "staffwatch" -> {
                if (args.length < 3 || !sender.hasPermission("meranked.staff")) return false;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    services.messages().send(sender, "general.player-not-found");
                    return true;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                services.watchlist().add(player, target, reason);
                services.messages().send(sender, "staff.watchlist-added", Map.of("player", target.getName()));
            }
            default -> services.gui().openRankedMenu(player);
        }
        return true;
    }

    private boolean anyEditing() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (services.kitEditor().isEditing(p.getUniqueId())) return true;
        }
        return false;
    }

    private void handleCancel(Player player) {
        UUID uuid = player.getUniqueId();
        var matchOpt = services.matches().getMatch(uuid);
        if (matchOpt.isPresent()) {
            var match = matchOpt.get();
            switch (match.state()) {
                case VOTING, CINEMATIC, COUNTDOWN -> {
                    services.antiDodge().recordDodge(uuid);
                    services.matches().handleLeaveDuringMatch(player);
                    services.messages().send(player, "queue.cancel-dodge");
                }
                default -> services.messages().send(player, "queue.in-match");
            }
            return;
        }
        if (services.queue().isQueued(uuid)) {
            services.queue().leaveQueue(uuid);
            services.messages().send(player, "queue.cancelled");
        } else {
            services.messages().send(player, "queue.not-queued");
        }
    }

    private String resolveGamemode(String input) {
        if (input == null) return null;
        for (String mode : services.profiles().enabledGamemodes()) {
            if (mode.equalsIgnoreCase(input)) return mode;
        }
        String normalized = input.replace("_", " ");
        for (String mode : services.profiles().enabledGamemodes()) {
            if (mode.equalsIgnoreCase(normalized)) return mode;
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("queue", "leave", "cancel", "card", "fairness", "staff", "reload", "alerts", "profile",
                    "leaderboard", "rollback", "rollbackplayer", "rollbackgroup", "rollbackpreview", "suspicion",
                    "suspicious", "staffwatch", "unwatch", "watchlist", "dodgeinfo", "cleardodges", "history",
                    "match", "note", "notes", "matchnotes", "evidence", "punish", "unpunish", "queueban",
                    "unqueueban", "lockqueue", "unlockqueue", "setdefaultkit", "setrating", "setrd", "reset",
                    "ban", "unban", "forcedecay", "startseason", "endseason", "queuehide", "queueunhide",
                    "lowersuspicion", "clearsuspicion", "replayinfo", "deletereplay", "kit"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("profile")
                || args[0].equalsIgnoreCase("leaderboard"))) {
            return filter(services.profiles().enabledGamemodes(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("suspicion")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            return filter(List.of("audit", "view", "copy", "reset", "resetall"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(input.toLowerCase())) result.add(opt);
        }
        return result;
    }
}
