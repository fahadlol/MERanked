package com.meranked.core.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import org.bukkit.Bukkit;
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

public final class StaffNoteCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public StaffNoteCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!staff.hasPermission("meranked.staff.note")) {
            staff.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            staff.sendMessage("§cUsage: /staffnote <player> <note>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found.");
            return true;
        }
        String note = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        services.staffNotes().addNote(staff.getUniqueId(), "PLAYER", target.getUniqueId().toString(), note, "STAFF_ONLY");
        services.logger().logStaff(LogCategory.STAFF, "STAFF_NOTE_ADDED", LogSeverity.INFO,
                staff.getName() + " added note on " + target.getName(),
                staff.getName(), staff.getUniqueId(),
                java.util.Map.of("note", note, "target", target.getName()));
        staff.sendMessage("§aNote added for " + target.getName());
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
