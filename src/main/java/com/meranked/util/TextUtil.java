package com.meranked.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private TextUtil() {}

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return MINI.deserialize(input);
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        String resolved = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace("<" + entry.getKey() + ">", entry.getValue() == null ? "" : entry.getValue());
                resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return parse(resolved);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Converts a MiniMessage string to legacy section-code text (for PlaceholderAPI output). */
    public static String stripToLegacy(String miniMessage) {
        return LegacyComponentSerializer.legacySection().serialize(parse(miniMessage));
    }

    public static void send(Player player, String message) {
        player.sendMessage(parse(message));
    }

    public static void send(Player player, String message, Map<String, String> placeholders) {
        player.sendMessage(parse(message, placeholders));
    }

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(
                parse(title),
                parse(subtitle),
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long rem = seconds % 60;
        if (minutes < 60) return minutes + "m " + rem + "s";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    public static String formatMatchTime(long millis) {
        long seconds = millis / 1000;
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
