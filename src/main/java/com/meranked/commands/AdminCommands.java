package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.RankedProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

final class
AdminCommands {

    private AdminCommands() {}

    static boolean handle(ServiceRegistry services, CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("meranked.admin") && !sub.equals("alerts") && !sub.equals("suspicion")
                && !sub.equals("dodgeinfo") && !sub.equals("cleardodges") && !sub.equals("rollback")
                && !sub.equals("staffwatch") && !sub.equals("unwatch") && !sub.equals("watchlist")
                && !sub.equals("kit") && !sub.equals("replay") && !sub.equals("replays")) {
            if (sub.equals("setrating") || sub.equals("setrd") || sub.equals("reset") || sub.equals("ban")
                    || sub.equals("startseason") || sub.equals("endseason") || sub.equals("queuehide")) {
                services.messages().send(sender, "general.no-permission");
                return true;
            }
        }

        return switch (sub) {
            case "setrating" -> {
                if (args.length < 4) yield false;
                UUID uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                RankedProfile p = services.profiles().getProfile(uuid, args[2]);
                p.setRating(Double.parseDouble(args[3]));
                p.setTier(services.tiers().getTierForRating(p.rating(), false));
                services.profiles().queueSave(p);
                sender.sendMessage("§aRating set.");
                yield true;
            }
            case "setrd" -> {
                if (args.length < 4) yield false;
                UUID uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                RankedProfile p = services.profiles().getProfile(uuid, args[2]);
                p.setRatingDeviation(Double.parseDouble(args[3]));
                services.profiles().queueSave(p);
                sender.sendMessage("§aRD set.");
                yield true;
            }
            case "reset" -> {
                if (args.length < 3) yield false;
                UUID uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                services.kits().resetKit(uuid, args[2]);
                RankedProfile fresh = new RankedProfile(uuid, args[2]);
                services.profiles().queueSave(fresh);
                sender.sendMessage("§aProfile reset.");
                yield true;
            }
            case "ban" -> {
                if (args.length < 3) yield false;
                UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                long expires = 0;
                int reasonStart = 2;
                if (args.length >= 4 && isDuration(args[2])) {
                    expires = System.currentTimeMillis() + parseDuration(args[2]);
                    reasonStart = 3;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length));
                services.bans().ban(target, reason, sender.getName(), expires);
                services.queue().removeFromQueue(target);
                sender.sendMessage("§aPlayer banned from ranked" + (expires > 0 ? " (temp)." : "."));
                yield true;
            }
            case "unban" -> {
                if (args.length < 2) yield false;
                services.bans().unban(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                sender.sendMessage("§aUnbanned.");
                yield true;
            }
            case "history" -> {
                if (!staffPerm(sender)) { services.messages().send(sender, "general.no-permission"); yield true; }
                if (args.length < 2) yield false;
                showHistory(services, sender, Bukkit.getOfflinePlayer(args[1]).getUniqueId(), args[1]);
                yield true;
            }
            case "match" -> {
                if (!staffPerm(sender)) { services.messages().send(sender, "general.no-permission"); yield true; }
                if (args.length < 2) yield false;
                showMatch(services, sender, args[1]);
                yield true;
            }
            case "suspicious" -> {
                if (!staffPerm(sender)) { services.messages().send(sender, "general.no-permission"); yield true; }
                showSuspicious(services, sender);
                yield true;
            }
            case "rollbackplayer" -> {
                if (!sender.hasPermission("meranked.admin")) { services.messages().send(sender, "general.no-permission"); yield true; }
                if (args.length < 3 || !(sender instanceof Player staff)) yield false;
                UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                int count = Integer.parseInt(args[2]);
                String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "staff rollback";
                int done = services.rollback().rollbackPlayerMatches(staff, target, count, reason);
                sender.sendMessage("§aRolled back " + done + " match(es).");
                yield true;
            }
            case "rollbackgroup" -> {
                if (!sender.hasPermission("meranked.admin")) { services.messages().send(sender, "general.no-permission"); yield true; }
                if (args.length < 4 || !(sender instanceof Player staff)) yield false;
                UUID a = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                UUID b = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
                int count = Integer.parseInt(args[3]);
                String reason = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : "staff rollback";
                int done = services.rollback().rollbackBetween(staff, a, b, count, reason);
                sender.sendMessage("§aRolled back " + done + " match(es) between players.");
                yield true;
            }
            case "forcedecay" -> {
                if (args.length < 3 || !(sender instanceof Player staff)) yield false;
                services.decay().forceDecay(staff, Bukkit.getOfflinePlayer(args[1]).getUniqueId(), args[2]);
                sender.sendMessage("§aDecay applied.");
                yield true;
            }
            case "startseason" -> {
                String name = args.length > 1 ? args[1] : "Season " + (services.seasons().currentSeasonId() + 1);
                services.seasons().startSeason(name);
                sender.sendMessage("§aSeason started: " + name);
                yield true;
            }
            case "endseason" -> {
                services.seasons().endSeason();
                sender.sendMessage("§aSeason ended.");
                yield true;
            }
            case "queuehide" -> {
                if (args.length < 4) yield false;
                long ms = parseDuration(args[2]);
                services.antiDodge().hideQueue(Bukkit.getOfflinePlayer(args[1]).getUniqueId(), ms,
                        String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                sender.sendMessage("§aQueue hidden.");
                yield true;
            }
            case "queueunhide" -> {
                if (args.length < 2) yield false;
                services.antiDodge().unhideQueue(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                sender.sendMessage("§aQueue unhidden.");
                yield true;
            }
            case "lowersuspicion" -> {
                if (args.length < 3) yield false;
                services.suspicion().lowerScore(Bukkit.getOfflinePlayer(args[1]).getUniqueId(), Integer.parseInt(args[2]));
                yield true;
            }
            case "clearsuspicion" -> {
                if (args.length < 2) yield false;
                services.suspicion().clearScore(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                yield true;
            }
            case "watchlist" -> {
                if (sender instanceof Player p) services.gui().openWatchlist(p);
                yield true;
            }
            case "unwatch" -> {
                if (args.length < 2) yield false;
                services.watchlist().remove(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                yield true;
            }
            case "setdefaultkit" -> {
                if (!(sender instanceof Player admin) || args.length < 2) yield false;
                services.defaultKits().beginSetDefault(admin, args[1]);
                var pending = services.defaultKits().getPending(admin.getUniqueId());
                if (pending != null) services.gui().openDefaultKitConfirm(admin, pending);
                yield true;
            }
            case "replayinfo" -> {
                if (args.length < 2) yield false;
                if (sender instanceof Player p) services.gui().openReplay(p, args[1]);
                yield true;
            }
            case "deletereplay" -> {
                if (args.length < 2 || !sender.hasPermission("meranked.admin")) yield false;
                services.database().executeAsync(conn -> {
                    try (var ps = conn.prepareStatement("DELETE FROM ranked_replays WHERE match_id = ?")) {
                        ps.setString(1, args[1]);
                        ps.executeUpdate();
                    }
                });
                sender.sendMessage("§aReplay deleted.");
                yield true;
            }
            case "kit" -> handleKitSub(services, sender, args);
            default -> false;
        };
    }

    private static boolean handleKitSub(ServiceRegistry services, CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        String kitSub = args[1].toLowerCase();
        if (kitSub.equals("audit") && args.length >= 4 && sender instanceof Player staff) {
            services.gui().openKitAudit(staff, Bukkit.getOfflinePlayer(args[2]).getUniqueId(), args[3]);
            return true;
        }
        if (kitSub.equals("reset") && args.length >= 4) {
            services.kits().resetKit(Bukkit.getOfflinePlayer(args[2]).getUniqueId(), args[3]);
            sender.sendMessage("§aKit reset.");
            return true;
        }
        if (kitSub.equals("resetall") && args.length >= 3) {
            for (String mode : services.profiles().enabledGamemodes()) {
                services.kits().resetKit(Bukkit.getOfflinePlayer(args[2]).getUniqueId(), mode);
            }
            sender.sendMessage("§aAll kits reset.");
            return true;
        }
        if (kitSub.equals("view") && args.length >= 4 && sender instanceof Player staff) {
            services.gui().openKitAudit(staff, Bukkit.getOfflinePlayer(args[2]).getUniqueId(), args[3]);
            return true;
        }
        if (kitSub.equals("copy") && args.length >= 5) {
            UUID from = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
            UUID to = Bukkit.getOfflinePlayer(args[3]).getUniqueId();
            services.kits().copyKit(from, to, args[4]);
            sender.sendMessage("§aKit copied.");
            return true;
        }
        return false;
    }

    private static void showHistory(ServiceRegistry services, CommandSender sender, UUID uuid, String name) {
        services.database().executeAsync(conn -> {
            try (var ps = conn.prepareStatement("""
                SELECT m.match_id, m.gamemode, m.winner, m.loser, m.ended_at
                FROM ranked_matches m
                WHERE m.winner = ? OR m.loser = ?
                ORDER BY m.ended_at DESC LIMIT 10
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());
                try (var rs = ps.executeQuery()) {
                    sender.sendMessage("§6§lMatch history: §e" + name);
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        boolean won = uuid.toString().equals(rs.getString("winner"));
                        sender.sendMessage((won ? "§a[WIN] " : "§c[LOSS] ") + "§7" + rs.getString("gamemode")
                                + " §8" + rs.getString("match_id"));
                    }
                    if (!any) sender.sendMessage("§7No recorded matches.");
                }
            }
        });
    }

    private static void showMatch(ServiceRegistry services, CommandSender sender, String matchId) {
        services.database().executeAsync(conn -> {
            try (var ps = conn.prepareStatement("""
                SELECT mp.uuid, mp.rating_before, mp.rating_after, mp.tier_before, mp.tier_after, mp.ping
                FROM ranked_match_participants mp WHERE mp.match_id = ?
                """)) {
                ps.setString(1, matchId);
                try (var rs = ps.executeQuery()) {
                    sender.sendMessage("§6§lMatch §e" + matchId);
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        String pname = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid"))).getName();
                        sender.sendMessage("§e" + pname + " §7rating " + (int) rs.getDouble("rating_before")
                                + " §8-> §7" + (int) rs.getDouble("rating_after") + " §7tier "
                                + rs.getString("tier_before") + " -> " + rs.getString("tier_after"));
                    }
                    if (!any) sender.sendMessage("§7No participant data for that match.");
                }
            }
        });
    }

    private static void showSuspicious(ServiceRegistry services, CommandSender sender) {
        services.database().executeAsync(conn -> {
            try (var ps = conn.prepareStatement("""
                SELECT uuid, username, suspicion_score FROM ranked_players
                WHERE suspicion_score > 0 ORDER BY suspicion_score DESC LIMIT 15
                """)) {
                try (var rs = ps.executeQuery()) {
                    sender.sendMessage("§6§lSuspicious players:");
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        String pname = rs.getString("username");
                        int score = rs.getInt("suspicion_score");
                        String color = score >= 70 ? "§c" : score >= 40 ? "§e" : "§7";
                        sender.sendMessage(color + pname + " §8- §7score " + score);
                    }
                    if (!any) sender.sendMessage("§7No flagged players.");
                }
            }
        });
    }

    private static boolean staffPerm(CommandSender sender) {
        return sender.hasPermission("meranked.staff") || sender.hasPermission("meranked.admin");
    }

    private static boolean isDuration(String input) {
        return input.matches("\\d+[smhd]?");
    }

    private static long parseDuration(String input) {
        if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86400000L;
        if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60000L;
        if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3600000L;
        if (input.endsWith("s")) return Long.parseLong(input.replace("s", "")) * 1000L;
        return Long.parseLong(input) * 1000L;
    }
}
