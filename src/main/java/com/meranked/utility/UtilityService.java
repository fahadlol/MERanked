package com.meranked.utility;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.matches.MatchService;
import com.meranked.settings.PlayerSettingsService;
import com.meranked.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UtilityService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;
    private final MatchService matchService;
    private final PlayerSettingsService settings;
    private final Map<UUID, UUID> lastMessageTarget = new HashMap<>();
    private final Set<UUID> socialSpy = new HashSet<>();

    public UtilityService(MERankedPlugin plugin, ConfigService configService,
                           MessageService messages, MatchService matchService,
                           PlayerSettingsService settings) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
        this.matchService = matchService;
        this.settings = settings;
    }

    public void teleportSpawn(Player player) {
        FileConfiguration config = configService.get("config.yml");
        var loc = LocationUtil.fromConfig(config.getConfigurationSection("spawn"));
        if (loc != null) player.teleport(loc);
        plugin.services().lobbyItems().giveLobbyItems(player);
        messages.sendPrefixed(player, "utility.spawn");
    }

    public void setSpawn(Player player) {
        FileConfiguration config = configService.load("config.yml");
        LocationUtil.writeLocation(config.createSection("spawn"), player.getLocation());
        configService.save("config.yml", config);
    }

    public void sendPing(Player sender, Player target) {
        if (target == null) {
            messages.sendPrefixed(sender, "utility.ping-self", Map.of("ping", String.valueOf(sender.getPing())));
        } else {
            messages.sendPrefixed(sender, "utility.ping-other", Map.of("player", target.getName(), "ping", String.valueOf(target.getPing())));
        }
    }

    public void sendPrivateMessage(Player sender, Player target, String message) {
        if (!settings.get(target.getUniqueId()).messagesEnabled()) {
            messages.send(sender, "utility.msg-disabled");
            return;
        }
        if (isCommandBlocked(sender)) {
            messages.send(sender, "general.command-blocked-in-match");
            return;
        }
        lastMessageTarget.put(sender.getUniqueId(), target.getUniqueId());
        lastMessageTarget.put(target.getUniqueId(), sender.getUniqueId());

        Map<String, String> sentPh = Map.of("target", target.getName(), "message", message);
        Map<String, String> recvPh = Map.of("sender", sender.getName(), "message", message);
        messages.send(sender, "utility.msg-format-sent", sentPh);
        messages.send(target, "utility.msg-format-received", recvPh);

        for (UUID spy : socialSpy) {
            Player staff = Bukkit.getPlayer(spy);
            if (staff != null) {
                staff.sendMessage("§7[Spy] §f" + sender.getName() + " → " + target.getName() + ": §7" + message);
            }
        }
    }

    public void reply(Player sender, String message) {
        UUID targetId = lastMessageTarget.get(sender.getUniqueId());
        if (targetId == null) {
            messages.send(sender, "utility.no-reply-target");
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            messages.send(sender, "general.player-not-found");
            return;
        }
        sendPrivateMessage(sender, target, message);
    }

    public void toggleMsg(Player player) {
        settings.toggle(player.getUniqueId(), "messages");
    }

    public void toggleSocialSpy(Player player) {
        if (socialSpy.contains(player.getUniqueId())) socialSpy.remove(player.getUniqueId());
        else socialSpy.add(player.getUniqueId());
    }

    public boolean isCommandBlocked(Player player) {
        if (!matchService.isInMatch(player.getUniqueId())) return false;
        FileConfiguration config = configService.get("config.yml");
        if (!config.getBoolean("match.block-commands", true)) return false;
        return !player.hasPermission("meranked.admin");
    }

    public void showHelp(Player player) {
        FileConfiguration config = configService.get("utility.yml");
        for (String line : config.getStringList("help-lines")) {
            player.sendMessage(com.meranked.util.TextUtil.parse(line));
        }
    }

    public void showDiscord(Player player) {
        String link = configService.get("config.yml").getString("discord-link", "https://discord.gg/meranked");
        player.sendMessage(com.meranked.util.TextUtil.parse("<gradient:#D6B36A:#7C3AED><bold>Discord</bold></gradient> <gray>»</gray> <click:open_url:'" + link + "'><u>" + link + "</u></click>"));
    }

    public void showRules(Player player) {
        String link = configService.get("config.yml").getString("rules-link", "https://meranked.gg/rules");
        player.sendMessage(com.meranked.util.TextUtil.parse("<gray>Rules:</gray> <click:open_url:'" + link + "'><u>" + link + "</u></click>"));
    }
}
