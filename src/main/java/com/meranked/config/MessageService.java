package com.meranked.config;

import com.meranked.MERankedPlugin;
import com.meranked.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class MessageService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private FileConfiguration messages;

    public MessageService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        reload();
    }

    public void reload() {
        messages = configService.load("messages.yml");
    }

    public String raw(String path) {
        return messages.getString(path, "<red>Missing message: " + path + "</red>");
    }

    public Component get(String path) {
        return parse(raw(path));
    }

    public Component get(String path, Map<String, String> placeholders) {
        return parse(raw(path), placeholders);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }

    public void sendPrefixed(CommandSender sender, String path, Map<String, String> placeholders) {
        Map<String, String> all = new HashMap<>(placeholders == null ? Map.of() : placeholders);
        all.putIfAbsent("prefix", plainPrefix());
        send(sender, path, all);
    }

    public void sendPrefixed(CommandSender sender, String path) {
        sendPrefixed(sender, path, Map.of("prefix", plainPrefix()));
    }

    public void actionBar(Player player, String path, Map<String, String> placeholders) {
        player.sendActionBar(get(path, placeholders));
    }

    /** Parses an arbitrary MiniMessage string, resolving the &lt;prefix&gt; token. */
    public Component format(String input) {
        return parse(input);
    }

    public Component format(String input, Map<String, String> placeholders) {
        return parse(input, placeholders);
    }

    public String prefix() {
        return messages.getString("prefix", "");
    }

    private Component parse(String input) {
        String prefix = messages.getString("prefix", "");
        input = input.replace("<prefix>", prefix);
        return TextUtil.parse(input);
    }

    private Component parse(String input, Map<String, String> placeholders) {
        String prefix = messages.getString("prefix", "");
        input = input.replace("<prefix>", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                input = input.replace("<" + e.getKey() + ">", e.getValue() == null ? "" : e.getValue());
            }
        }
        return TextUtil.parse(input);
    }

    private String plainPrefix() {
        return TextUtil.plain(parse(messages.getString("prefix", "")));
    }
}
