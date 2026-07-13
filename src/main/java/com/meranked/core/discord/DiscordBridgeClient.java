package com.meranked.core.discord;

public interface DiscordBridgeClient {

    void connect();

    void disconnect();

    boolean isConnected();

    boolean send(String json);

    void setMessageListener(MessageListener listener);

    @FunctionalInterface
    interface MessageListener {
        void onMessage(String message);
    }
}
