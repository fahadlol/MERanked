package com.meranked.core.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BridgeCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public BridgeCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/bridge status §7| §e/bridge reload §7| §e/bridge test <category>");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "test" -> test(sender, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand.");
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("meranked.bridge.status")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var bridge = services.discordBridge();
        sender.sendMessage("§6Discord Bridge");
        sender.sendMessage("§7Connected: §f" + (bridge.isConnected() ? "§aYes" : "§cNo"));
        sender.sendMessage("§7Queued events: §f" + bridge.queueSize());
        sender.sendMessage("§7Server ID: §f" + bridge.config().serverId());
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("meranked.bridge.reload")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        services.discordBridge().reloadConfig();
        sender.sendMessage("§aDiscord bridge config reloaded.");
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (!sender.hasPermission("meranked.bridge.test")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bridge test <category>");
            return true;
        }
        try {
            LogCategory category = java.util.Arrays.stream(LogCategory.values())
                    .filter(c -> c.configKey().equalsIgnoreCase(args[1]) || c.name().equalsIgnoreCase(args[1].replace("-", "_")))
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
            services.discordBridge().sendTestLog(category);
            sender.sendMessage("§aTest log sent to " + category.id());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cInvalid category. Use: staff, queue, match, suspicion, report, system, arena, punishment, discord");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("status", "reload", "test"));
        }
        if (args.length == 2 && "test".equalsIgnoreCase(args[0])) {
            return filter(args[1], java.util.Arrays.stream(LogCategory.values()).map(LogCategory::configKey).toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
