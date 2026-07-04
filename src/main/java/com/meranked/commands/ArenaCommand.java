package com.meranked.commands;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.Arena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ArenaCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public ArenaCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!sender.hasPermission("meranked.arena.admin")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) return false;

        String sub = args[0].toLowerCase();
        String name = args[1];
        Arena arena = services.arenas().getArena(name).orElseGet(() -> {
            if (sub.equals("create")) return services.arenas().createArena(name);
            return null;
        });
        if (arena == null && !sub.equals("list")) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }

        switch (sub) {
            case "create" -> sender.sendMessage("§aArena created: " + name);
            case "delete" -> services.arenas().deleteArena(name);
            case "setspawn1" -> { arena.setSpawn1(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setspawn2" -> { arena.setSpawn2(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setspectator" -> { arena.setSpectatorSpawn(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setintro1" -> { arena.setIntro1(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setintro2" -> { arena.setIntro2(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setintrocamera" -> { arena.setIntroCamera(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setpos1" -> { arena.setPos1(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setpos2" -> { arena.setPos2(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "setclone" -> { arena.setCloneSource(player.getLocation()); services.arenas().saveArenaAsync(arena); }
            case "enable" -> { arena.setEnabled(true); arena.setBroken(false); services.arenas().saveArenaAsync(arena); }
            case "disable" -> { arena.setEnabled(false); services.arenas().saveArenaAsync(arena); }
            case "allow" -> { if (args.length >= 3) { arena.allowedGamemodes().add(args[2]); services.arenas().saveArenaAsync(arena); } }
            case "blacklist" -> {
                if (args.length >= 3) {
                    arena.blockedGamemodes().add(args[2]);
                    services.arenas().saveArenaAsync(arena);
                }
            }
            case "list" -> {
                sender.sendMessage("§6Arenas:");
                services.arenas().allArenas().forEach(a ->
                        sender.sendMessage("§7- §f" + a.name() + " §8[" + (a.enabled() ? "§aON" : "§cOFF") + "§8]"));
            }
            case "tag" -> {
                if (args.length >= 3) {
                    String tag = args[2];
                    if (!arena.tags().remove(tag)) arena.tags().add(tag);
                    services.arenas().saveArenaAsync(arena);
                    sender.sendMessage("§aTags: " + String.join(", ", arena.tags()));
                }
            }
            case "description", "desc" -> {
                if (args.length >= 3) {
                    arena.setDescription(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                    services.arenas().saveArenaAsync(arena);
                    sender.sendMessage("§aDescription set.");
                }
            }
            case "regen" -> services.arenas().regenerateArenaAsync(arena, 0, () -> sender.sendMessage("§aRegeneration queued."));
            case "saveclone" -> services.arenas().saveCloneAsync(arena, ok ->
                    sender.sendMessage(ok ? "§aClone/schematic saved for " + name + "." : "§cSave failed. Check pos1/pos2."));
            case "fix" -> {
                if (services.arenas().fixArena(arena)) sender.sendMessage("§aArena fixed and enabled.");
                else sender.sendMessage("§cArena still has issues. Run /arena test " + name);
            }
            case "test" -> {
                var result = services.arenas().testArena(arena);
                if (result.passed()) sender.sendMessage("§aArena test passed.");
                else result.issues().forEach(i -> sender.sendMessage("§c- " + i));
            }
            case "broken" -> services.arenas().allArenas().stream().filter(Arena::broken)
                    .forEach(a -> sender.sendMessage("§c" + a.name() + ": " + a.brokenReason()));
            default -> sender.sendMessage("§cUnknown arena subcommand.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "setspawn1", "setspawn2", "list", "enable", "disable", "regen");
        }
        return List.of();
    }
}
