package com.meranked.core.discord;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import com.meranked.core.logs.MerankedLogEvent;
import com.meranked.core.logs.MerankedLoggerService;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordBridgeManager {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final MerankedLoggerService logger;
    private DiscordBridgeConfig config;
    private DiscordBridgeClient client;
    private final DiscordBridgeQueue queue;
    private DiscordRequestHandler requestHandler;
    private BridgeInboundServer inboundServer;
    private int reconnectTaskId = -1;
    private int flushTaskId = -1;
    private int heartbeatTaskId = -1;
    private final Map<String, Long> recentEventKeys = new ConcurrentHashMap<>();
    private static final long DEDUP_MS = 2000L;

    public DiscordBridgeManager(MERankedPlugin plugin, ServiceRegistry services, MerankedLoggerService logger) {
        this.plugin = plugin;
        this.services = services;
        this.logger = logger;
        this.config = new DiscordBridgeConfig(services.config().get("config.yml"));
        this.queue = new DiscordBridgeQueue(config.maxQueuedEvents());
    }

    public void start() {
        reloadConfig();
        if (!config.enabled()) {
            plugin.getLogger().info("[DiscordBridge] Disabled in config.");
            return;
        }
        requestHandler = new DiscordRequestHandler(plugin, services, logger, this);
        client = config.websocketMode()
                ? new WebSocketDiscordBridgeClient(plugin, config)
                : new HttpDiscordBridgeClient(plugin, config);

        client.setMessageListener(msg -> plugin.tasks().runAsync(() ->
                requestHandler.handle(msg, response -> {
                    if (client.isConnected()) client.send(response);
                })));

        startInboundServer();
        connect();

        long reconnectTicks = config.reconnectDelaySeconds() * 20L;
        reconnectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!config.enabled()) return;
            if (!client.isConnected()) {
                connect();
            }
        }, reconnectTicks, reconnectTicks);

        flushTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::flushQueue, 40L, 40L);

        if (config.websocketMode()) {
            long heartbeatTicks = config.heartbeatSeconds() * 20L;
            heartbeatTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (client.isConnected()) {
                    client.send(DiscordBridgeEvent.heartbeat(config.serverId()));
                }
            }, heartbeatTicks, heartbeatTicks);
        }

        logger.log(LogCategory.SYSTEM, "DISCORD_BRIDGE_STARTING", LogSeverity.INFO,
                "Discord bridge starting in " + (config.websocketMode() ? "websocket" : "http") + " mode");
    }

    public void shutdown() {
        if (reconnectTaskId != -1) Bukkit.getScheduler().cancelTask(reconnectTaskId);
        if (flushTaskId != -1) Bukkit.getScheduler().cancelTask(flushTaskId);
        if (heartbeatTaskId != -1) Bukkit.getScheduler().cancelTask(heartbeatTaskId);
        if (inboundServer != null) inboundServer.stop();
        if (client != null) {
            if (client.isConnected()) {
                logger.log(LogCategory.SYSTEM, "DISCORD_BRIDGE_DISCONNECTED", LogSeverity.MEDIUM,
                        "Discord bridge disconnected (shutdown)");
            }
            client.disconnect();
        }
    }

    public void reloadConfig() {
        config = new DiscordBridgeConfig(services.config().get("config.yml"));
    }

    public void enqueue(MerankedLogEvent event) {
        if (event == null || !config.enabled()) return;
        String dedupKey = event.category().id() + ":" + event.type() + ":" + event.summary();
        long now = System.currentTimeMillis();
        Long last = recentEventKeys.put(dedupKey, now);
        if (last != null && now - last < DEDUP_MS) return;
        recentEventKeys.entrySet().removeIf(e -> now - e.getValue() > 60_000L);

        if (client != null && client.isConnected()) {
            plugin.tasks().runAsync(() -> {
                boolean sent = client.send(DiscordBridgeEvent.toJson(event));
                if (!sent && config.queueOfflineEvents()) {
                    queue.enqueue(event);
                    warnOffline();
                }
            });
        } else if (config.queueOfflineEvents()) {
            queue.enqueue(event);
            warnOffline();
        }
    }

    private void flushQueue() {
        if (client == null || !client.isConnected() || queue.isEmpty()) return;
        plugin.tasks().runAsync(() -> {
            MerankedLogEvent event;
            while (client.isConnected() && (event = queue.poll()) != null) {
                if (!client.send(DiscordBridgeEvent.toJson(event))) {
                    queue.enqueue(event);
                    break;
                }
            }
        });
    }

    private void connect() {
        plugin.tasks().runAsync(() -> {
            try {
                client.connect();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (client.isConnected()) {
                        logger.log(LogCategory.SYSTEM, "DISCORD_BRIDGE_CONNECTED", LogSeverity.INFO,
                                "Discord bridge connected");
                        flushQueue();
                    } else {
                        logger.log(LogCategory.SYSTEM, "DISCORD_BRIDGE_RECONNECT_FAILED", LogSeverity.LOW,
                                "Discord bridge reconnect failed");
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("[DiscordBridge] Connect error: " + ex.getMessage());
            }
        });
    }

    private void warnOffline() {
        if (config.debug()) {
            plugin.getLogger().warning("[DiscordBridge] Bot offline — event queued (" + queue.size() + ")");
        }
    }

    private void startInboundServer() {
        try {
            inboundServer = new BridgeInboundServer(config.inboundPort(), config.inboundPath(), config.secretToken(),
                    body -> requestHandler.handle(body, response -> {
                        if (client != null && client.isConnected()) {
                            client.send(response);
                        }
                    }));
            inboundServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            if (config.debug()) {
                plugin.getLogger().info("[DiscordBridge] Inbound API on port " + config.inboundPort());
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[DiscordBridge] Failed to start inbound server: " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public int queueSize() {
        return queue.size();
    }

    public DiscordBridgeConfig config() {
        return config;
    }

    public void sendTestLog(LogCategory category) {
        logger.log(MerankedLogEvent.builder(config.serverId(), category, "TEST_EVENT", LogSeverity.LOW,
                        "Test log from MERanked bridge")
                .put("test", true)
                .build());
    }

    private static final class BridgeInboundServer extends NanoHTTPD {

        private final String path;
        private final String token;
        private final java.util.function.Consumer<String> handler;

        BridgeInboundServer(int port, String path, String token, java.util.function.Consumer<String> handler) {
            super(port);
            this.path = path;
            this.token = token;
            this.handler = handler;
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (!session.getUri().equals(path)) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
            }
            String auth = session.getHeaders().get("authorization");
            if (auth == null || !auth.equals("Bearer " + token)) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized");
            }
            try {
                Map<String, String> body = new java.util.HashMap<>();
                session.parseBody(body);
                String json = body.getOrDefault("postData", "");
                handler.accept(json);
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
            } catch (Exception ex) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", ex.getMessage());
            }
        }
    }
}
