package com.meranked.core.logs;

public enum LogCategory {
    STAFF("staff-logs", "staff"),
    QUEUE("queue-logs", "queue"),
    MATCH("match-logs", "match"),
    SUSPICION("suspicion-logs", "suspicion"),
    REPORT("report-logs", "report"),
    SYSTEM("system-logs", "system"),
    ARENA("arena-logs", "arena"),
    PUNISHMENT("punishment-logs", "punishment"),
    DISCORD("discord-logs", "discord");

    private final String id;
    private final String configKey;

    LogCategory(String id, String configKey) {
        this.id = id;
        this.configKey = configKey;
    }

    public String id() {
        return id;
    }

    public String configKey() {
        return configKey;
    }
}
