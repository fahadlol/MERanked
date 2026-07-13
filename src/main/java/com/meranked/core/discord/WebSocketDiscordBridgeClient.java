package com.meranked.core.discord;

import com.meranked.MERankedPlugin;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSocketDiscordBridgeClient implements DiscordBridgeClient {

    private final MERankedPlugin plugin;
    private final DiscordBridgeConfig config;
    private final OkHttpClient client;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile MessageListener listener;
    private volatile WebSocket webSocket;

    public WebSocketDiscordBridgeClient(MERankedPlugin plugin, DiscordBridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(config.heartbeatSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void connect() {
        if (webSocket != null) {
            webSocket.close(1000, "reconnect");
        }
        Request request = new Request.Builder()
                .url(config.websocketUrl())
                .addHeader("Authorization", "Bearer " + config.secretToken())
                .addHeader("X-MERanked-Server", config.serverId())
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket socket, @NotNull Response response) {
                connected.set(true);
                if (config.debug()) {
                    plugin.getLogger().info("[DiscordBridge] WebSocket connected.");
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket socket, @NotNull String text) {
                MessageListener l = listener;
                if (l != null) l.onMessage(text);
            }

            @Override
            public void onClosing(@NotNull WebSocket socket, int code, @NotNull String reason) {
                connected.set(false);
                socket.close(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket socket, int code, @NotNull String reason) {
                connected.set(false);
            }

            @Override
            public void onFailure(@NotNull WebSocket socket, @NotNull Throwable t, @Nullable Response response) {
                connected.set(false);
                if (config.debug()) {
                    plugin.getLogger().warning("[DiscordBridge] WebSocket failure: " + t.getMessage());
                }
            }
        });
    }

    @Override
    public void disconnect() {
        connected.set(false);
        if (webSocket != null) {
            webSocket.close(1000, "shutdown");
            webSocket = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public boolean send(String json) {
        WebSocket socket = webSocket;
        if (socket == null || !connected.get()) return false;
        return socket.send(json);
    }

    @Override
    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }
}
