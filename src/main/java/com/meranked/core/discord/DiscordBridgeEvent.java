package com.meranked.core.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.core.logs.MerankedLogEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DiscordBridgeEvent {

    private static final Gson GSON = new GsonBuilder().create();

    private DiscordBridgeEvent() {}

    public static String toJson(MerankedLogEvent event) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("eventId", event.eventId());
        root.put("serverId", event.serverId());
        root.put("category", event.category().id());
        root.put("type", event.type());
        root.put("severity", event.severity().name());
        root.put("timestamp", event.timestamp());
        root.put("summary", event.summary());
        if (event.player() != null) root.put("player", event.player());
        if (event.staff() != null) root.put("staff", event.staff());
        root.put("data", event.data());
        return GSON.toJson(root);
    }

    public static String heartbeat(String serverId) {
        return GSON.toJson(Map.of(
                "type", "HEARTBEAT",
                "serverId", serverId,
                "timestamp", System.currentTimeMillis()
        ));
    }
}
