package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.database.DatabaseService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BanService {

    private final MERankedPlugin plugin;
    private final DatabaseService database;
    private final ConcurrentHashMap<UUID, BanEntry> cache = new ConcurrentHashMap<>();

    public BanService(MERankedPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
        loadAll();
    }

    public boolean isBanned(UUID uuid) {
        BanEntry entry = cache.get(uuid);
        if (entry == null) return false;
        if (entry.expiresAt() > 0 && System.currentTimeMillis() > entry.expiresAt()) {
            unban(uuid);
            return false;
        }
        return true;
    }

    public String banReason(UUID uuid) {
        BanEntry entry = cache.get(uuid);
        return entry == null ? "" : entry.reason();
    }

    public void ban(UUID uuid, String reason, String bannedBy, long expiresAt) {
        BanEntry entry = new BanEntry(uuid, reason, bannedBy, System.currentTimeMillis(), expiresAt);
        cache.put(uuid, entry);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_bans (uuid, reason, banned_by, banned_at, expires_at)
                VALUES (?,?,?,?,?)
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, reason);
                ps.setString(3, bannedBy);
                ps.setLong(4, entry.bannedAt());
                ps.setLong(5, expiresAt);
                ps.executeUpdate();
            }
        });
    }

    public void unban(UUID uuid) {
        cache.remove(uuid);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ranked_bans WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    private void loadAll() {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_bans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    cache.put(uuid, new BanEntry(uuid, rs.getString("reason"),
                            rs.getString("banned_by"), rs.getLong("banned_at"), rs.getLong("expires_at")));
                }
            }
        });
    }

    public record BanEntry(UUID uuid, String reason, String bannedBy, long bannedAt, long expiresAt) {}
}
