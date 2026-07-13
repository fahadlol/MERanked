package com.meranked.core.reports;

import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ReportsReviewCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public ReportsReviewCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("meranked.report.review")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("reports".equals(name) && args.length == 0) {
            listReports(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /" + name + " <id> [reason]");
            return true;
        }
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Players only for review commands.");
            return true;
        }
        String id = args[0];
        String notes = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
        ReportService.Status status = switch (name) {
            case "reportvalid" -> ReportService.Status.VALID;
            case "reportinvalid" -> ReportService.Status.INVALID;
            default -> ReportService.Status.REVIEWED;
        };
        services.reports().review(id, staff, status, notes);
        sender.sendMessage("§aReport " + id + " updated to " + status.name());
        return true;
    }

    private void listReports(CommandSender sender) {
        var reports = services.reports().openReports();
        if (reports.isEmpty()) {
            sender.sendMessage("§7No open reports.");
            return;
        }
        sender.sendMessage("§6Open Reports (" + reports.size() + ")");
        for (ReportService.ReportRecord r : reports) {
            sender.sendMessage("§e" + r.reportId() + " §7" + r.reporterName() + " → " + r.reportedName()
                    + " §8— §f" + r.reason());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return services.reports().openReports().stream()
                    .map(ReportService.ReportRecord::reportId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
