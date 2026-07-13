package com.meranked.core.discord;

import com.meranked.MERankedPlugin;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class HttpDiscordBridgeClient implements DiscordBridgeClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final MERankedPlugin plugin;
    private final DiscordBridgeConfig config;
    private final OkHttpClient client;
    private volatile boolean connected;

    public HttpDiscordBridgeClient(MERankedPlugin plugin, DiscordBridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void connect() {
        connected = true;
        if (config.debug()) {
            plugin.getLogger().info("[DiscordBridge] HTTP mode ready: " + config.httpUrl());
        }
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean send(String json) {
        if (!connected) return false;
        Request request = new Request.Builder()
                .url(config.httpUrl())
                .addHeader("Authorization", "Bearer " + config.secretToken())
                .addHeader("X-MERanked-Server", config.serverId())
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException ex) {
            if (config.debug()) {
                plugin.getLogger().warning("[DiscordBridge] HTTP send failed: " + ex.getMessage());
            }
            return false;
        }
    }

    @Override
    public void setMessageListener(MessageListener listener) {
        // HTTP mode uses inbound NanoHTTPD listener in DiscordBridgeManager.
    }
}
