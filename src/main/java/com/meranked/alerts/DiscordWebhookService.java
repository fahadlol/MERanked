package com.meranked.alerts;

import com.google.gson.Gson;
import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.model.AlertSeverity;
import com.meranked.model.StaffAlert;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

public final class DiscordWebhookService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public DiscordWebhookService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void sendAlert(StaffAlert alert) {
        FileConfiguration config = configService.get("staff-alerts.yml");
        if (!config.getBoolean("discord.enabled", false)) return;
        AlertSeverity min = AlertSeverity.valueOf(config.getString("discord.alert-min-severity", "MEDIUM"));
        if (!alert.severity().isAtLeast(min)) return;

        String webhookUrl = config.getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.contains("example")) return;

        Map<String, Object> embed = Map.of(
                "title", "MERanked Alert » " + alert.type(),
                "color", severityColor(alert.severity()),
                "fields", List.of(
                        field("Severity", alert.severity().name(), true),
                        field("Players", alert.players(), true),
                        field("Gamemode", alert.gamemode(), true),
                        field("Match ID", alert.matchId(), true),
                        field("Arena", alert.arena(), true),
                        field("Reason", alert.reason(), false)
                )
        );
        Map<String, Object> payload = Map.of("embeds", List.of(embed));
        String mentionRole = config.getString("discord.mention-role-id", "");
        if (!mentionRole.isEmpty()) {
            payload = Map.of("content", "<@&" + mentionRole + ">", "embeds", List.of(embed));
        }

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(gson.toJson(payload), MediaType.get("application/json")))
                .build();

        plugin.tasks().runAsync(() -> {
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("Discord webhook failed: " + response.code());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Discord webhook error: " + ex.getMessage());
            }
        });
    }

    /** Sends a raw content message to an arbitrary webhook URL (used by punishments/evidence). */
    public void sendRaw(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("example")) return;
        Map<String, Object> payload = Map.of("content", content);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(gson.toJson(payload), MediaType.get("application/json")))
                .build();
        plugin.tasks().runAsync(() -> {
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("Discord webhook failed: " + response.code());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Discord webhook error: " + ex.getMessage());
            }
        });
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        return Map.of("name", name, "value", value == null || value.isEmpty() ? "N/A" : value, "inline", inline);
    }

    private int severityColor(AlertSeverity severity) {
        return switch (severity) {
            case LOW -> 0x9CA3AF;
            case MEDIUM -> 0xD6B36A;
            case HIGH -> 0xEF4444;
            case CRITICAL -> 0x7C3AED;
        };
    }
}
