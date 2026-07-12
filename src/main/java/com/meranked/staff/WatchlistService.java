package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.alerts.AlertService;
import com.meranked.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WatchlistService {

    private final MERankedPlugin plugin;
    private final DatabaseService database;
    private final AlertService alertService;
    private final List<WatchEntry> cache = new java.util.concurrent.CopyOnWriteArrayList<>();

    public WatchlistService(MERankedPlugin plugin, DatabaseService database, AlertService alertService) {
        this.plugin = plugin;
        this.database = database;
        this.alertService = alertService;
        loadAsync();
    }

    public void add(Player staff, Player target, String reason) {
        WatchEntry entry = new WatchEntry(target.getUniqueId(), target.getName(), reason, staff.getName(), System.currentTimeMillis());
        cache.add(entry);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                INSERT OR REPLACE INTO ranked_staff_watchlist (uuid, reason, added_by, added_at) VALUES (?,?,?,?)
                """))) {
                ps.setString(1, target.getUniqueId().toString());
                ps.setString(2, reason);
                ps.setString(3, staff.getName());
                ps.setLong(4, entry.addedAt());
                ps.executeUpdate();
            }
        });
    }

    public void add(UUID uuid, String name, String addedBy, String reason) {
        WatchEntry entry = new WatchEntry(uuid, name, reason, addedBy, System.currentTimeMillis());
        cache.removeIf(e -> e.uuid().equals(uuid));
        cache.add(entry);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(database.sql("""
                INSERT OR REPLACE INTO ranked_staff_watchlist (uuid, reason, added_by, added_at) VALUES (?,?,?,?)
                """))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, reason);
                ps.setString(3, addedBy);
                ps.setLong(4, entry.addedAt());
                ps.executeUpdate();
            }
        });
    }

    public void remove(UUID uuid) {
        cache.removeIf(e -> e.uuid().equals(uuid));
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ranked_staff_watchlist WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public List<WatchEntry> all() {
        return List.copyOf(cache);
    }

    public void notifyIfWatched(UUID uuid, String event, String detail) {
        cache.stream().filter(e -> e.uuid().equals(uuid)).findFirst().ifPresent(entry -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("meranked.staff")) {
                    staff.sendMessage("§6[Watch] §f" + entry.name() + " §7» §e" + event + " §7- " + detail);
                }
            }
        });
    }

    private void loadAsync() {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_staff_watchlist");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cache.add(new WatchEntry(
                            UUID.fromString(rs.getString("uuid")),
                            Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid"))).getName(),
                            rs.getString("reason"),
                            rs.getString("added_by"),
                            rs.getLong("added_at")
                    ));
                }
            }
        });
    }

    public record WatchEntry(UUID uuid, String name, String reason, String addedBy, long addedAt) {}
}
