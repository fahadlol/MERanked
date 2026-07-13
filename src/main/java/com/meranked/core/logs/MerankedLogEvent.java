package com.meranked.core.logs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MerankedLogEvent {

    private final String eventId;
    private final String serverId;
    private final LogCategory category;
    private final String type;
    private final LogSeverity severity;
    private final long timestamp;
    private final String summary;
    private final Map<String, Object> player;
    private final Map<String, Object> staff;
    private final Map<String, Object> data;

    private MerankedLogEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.serverId = builder.serverId;
        this.category = builder.category;
        this.type = builder.type;
        this.severity = builder.severity;
        this.timestamp = builder.timestamp;
        this.summary = builder.summary;
        this.player = builder.player == null ? null : Collections.unmodifiableMap(builder.player);
        this.staff = builder.staff == null ? null : Collections.unmodifiableMap(builder.staff);
        this.data = Collections.unmodifiableMap(builder.data);
    }

    public String eventId() { return eventId; }
    public String serverId() { return serverId; }
    public LogCategory category() { return category; }
    public String type() { return type; }
    public LogSeverity severity() { return severity; }
    public long timestamp() { return timestamp; }
    public String summary() { return summary; }
    public Map<String, Object> player() { return player; }
    public Map<String, Object> staff() { return staff; }
    public Map<String, Object> data() { return data; }

    public static Builder builder(String serverId, LogCategory category, String type, LogSeverity severity, String summary) {
        return new Builder(serverId, category, type, severity, summary);
    }

    public static final class Builder {
        private final String eventId = UUID.randomUUID().toString();
        private final String serverId;
        private final LogCategory category;
        private final String type;
        private final LogSeverity severity;
        private final long timestamp = System.currentTimeMillis();
        private final String summary;
        private Map<String, Object> player;
        private Map<String, Object> staff;
        private final Map<String, Object> data = new HashMap<>();

        private Builder(String serverId, LogCategory category, String type, LogSeverity severity, String summary) {
            this.serverId = serverId;
            this.category = category;
            this.type = type;
            this.severity = severity;
            this.summary = summary;
        }

        public Builder player(String name, UUID uuid) {
            this.player = Map.of("name", name == null ? "Unknown" : name, "uuid", uuid == null ? "" : uuid.toString());
            return this;
        }

        public Builder staff(String name, UUID uuid) {
            this.staff = Map.of("name", name == null ? "Console" : name, "uuid", uuid == null ? "" : uuid.toString());
            return this;
        }

        public Builder put(String key, Object value) {
            if (value != null) data.put(key, value);
            return this;
        }

        public Builder data(Map<String, Object> extra) {
            if (extra != null) data.putAll(extra);
            return this;
        }

        public MerankedLogEvent build() {
            return new MerankedLogEvent(this);
        }
    }
}
