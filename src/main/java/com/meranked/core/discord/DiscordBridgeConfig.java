package com.meranked.core.discord;

import org.bukkit.configuration.file.FileConfiguration;

public final class DiscordBridgeConfig {

    private final boolean enabled;
    private final String mode;
    private final String websocketUrl;
    private final String httpUrl;
    private final String secretToken;
    private final String serverId;
    private final int reconnectDelaySeconds;
    private final int heartbeatSeconds;
    private final boolean queueOfflineEvents;
    private final int maxQueuedEvents;
    private final boolean debug;
    private final int inboundPort;
    private final String inboundPath;

    public DiscordBridgeConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("discord-bridge.enabled", false);
        this.mode = cfg.getString("discord-bridge.mode", "websocket");
        this.websocketUrl = cfg.getString("discord-bridge.websocket-url", "ws://127.0.0.1:8081/minecraft");
        this.httpUrl = cfg.getString("discord-bridge.http-url", "http://127.0.0.1:3000/api/minecraft/event");
        this.secretToken = cfg.getString("discord-bridge.secret-token", "CHANGE_ME");
        this.serverId = cfg.getString("discord-bridge.server-id", "meranked-main");
        this.reconnectDelaySeconds = cfg.getInt("discord-bridge.reconnect-delay-seconds", 5);
        this.heartbeatSeconds = cfg.getInt("discord-bridge.heartbeat-seconds", 30);
        this.queueOfflineEvents = cfg.getBoolean("discord-bridge.queue-offline-events", true);
        this.maxQueuedEvents = cfg.getInt("discord-bridge.max-queued-events", 500);
        this.debug = cfg.getBoolean("discord-bridge.debug", false);
        this.inboundPort = cfg.getInt("discord-bridge.inbound-port", 8082);
        this.inboundPath = cfg.getString("discord-bridge.inbound-path", "/bridge");
    }

    public boolean enabled() { return enabled; }
    public boolean websocketMode() { return "websocket".equalsIgnoreCase(mode); }
    public String websocketUrl() { return websocketUrl; }
    public String httpUrl() { return httpUrl; }
    public String secretToken() { return secretToken; }
    public String serverId() { return serverId; }
    public int reconnectDelaySeconds() { return reconnectDelaySeconds; }
    public int heartbeatSeconds() { return heartbeatSeconds; }
    public boolean queueOfflineEvents() { return queueOfflineEvents; }
    public int maxQueuedEvents() { return maxQueuedEvents; }
    public boolean debug() { return debug; }
    public int inboundPort() { return inboundPort; }
    public String inboundPath() { return inboundPath; }
}
