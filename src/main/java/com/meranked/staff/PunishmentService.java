package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central punishment system: server bans/mutes/kicks plus ranked-specific bans
 * (ranked ban, queue ban, spectate ban, chat mute).
 */
public final class PunishmentService {

    public enum Type {
        BAN, MUTE, KICK, RANKEDBAN, QUEUEBAN, SPECTATEBAN, CHATMUTE
    }

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    // Cache of active punishments per player for fast lookups.
    private final ConcurrentHashMap<UUID, List<Punishment>> cache = new ConcurrentHashMap<>();

    public PunishmentService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void loadAll() {
        cache.clear();
        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM ranked_punishments WHERE active = TRUE");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = read(rs);
                    if (p.isActive()) {
                        cache.computeIfAbsent(p.uuid(), k -> new ArrayList<>()).add(p);
                    }
                }
            }
        });
    }

    public Punishment punish(UUID target, UUID staff, Type type, String reason,
                             long durationMs, String evidenceMatchId) {
        String id = "P" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        long now = System.currentTimeMillis();
        long end = durationMs <= 0 ? 0 : now + durationMs;
        Punishment punishment = new Punishment(id, target, staff, type, reason, durationMs, now, end, true, evidenceMatchId, null);
        cache.computeIfAbsent(target, k -> new ArrayList<>()).add(punishment);

        services.database().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_punishments
                (punishment_id, uuid, staff_uuid, type, reason, duration_ms, start_time, end_time, active, evidence_match_id, notes)
                VALUES (?,?,?,?,?,?,?,?,TRUE,?,?)
                """)) {
                ps.setString(1, id);
                ps.setString(2, target.toString());
                ps.setString(3, staff == null ? "CONSOLE" : staff.toString());
                ps.setString(4, type.name());
                ps.setString(5, reason);
                ps.setLong(6, durationMs);
                ps.setLong(7, now);
                ps.setLong(8, end);
                ps.setString(9, evidenceMatchId);
                ps.setString(10, null);
                ps.executeUpdate();
            }
            logHistory(conn, id, target, "ISSUED", staff);
        });

        applyEffects(target, punishment);
        sendWebhook(punishment);
        return punishment;
    }

    private void applyEffects(UUID target, Punishment p) {
        Player online = Bukkit.getPlayer(target);
        FileConfiguration cfg = services.config().get("punishments.yml");
        switch (p.type()) {
            case BAN, RANKEDBAN -> {
                if (p.type() == Type.RANKEDBAN) {
                    services.bans().ban(target, p.reason(), staffName(p.staffUuid()), p.endTime());
                    services.queue().removeFromQueue(target);
                } else {
                    // Full server ban: register with the server ban list so the player cannot rejoin.
                    String name = Bukkit.getOfflinePlayer(target).getName();
                    java.util.Date expiry = p.endTime() == 0 ? null : new java.util.Date(p.endTime());
                    if (name != null) {
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                .addBan(name, p.reason(), expiry, staffName(p.staffUuid()));
                    }
                    if (online != null) {
                        online.kick(services.messages().format(
                                cfg.getString("punishments.messages.banned", "").replace("<reason>", p.reason())));
                    }
                }
            }
            case KICK -> {
                if (online != null) online.kick(services.messages().format(
                        cfg.getString("punishments.messages.kicked", "").replace("<reason>", p.reason())));
            }
            case QUEUEBAN -> services.queue().removeFromQueue(target);
            default -> {
                // MUTE / CHATMUTE / SPECTATEBAN handled at usage points via isMuted / isSpectateBanned
            }
        }
    }

    public void unpunish(String punishmentId, UUID staff) {
        services.database().executeAsync(conn -> {
            UUID target = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, type FROM ranked_punishments WHERE punishment_id = ?")) {
                ps.setString(1, punishmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        target = UUID.fromString(rs.getString("uuid"));
                        String type = rs.getString("type");
                        if ("RANKEDBAN".equals(type)) {
                            services.bans().unban(target);
                        } else if ("BAN".equals(type)) {
                            String name = Bukkit.getOfflinePlayer(target).getName();
                            if (name != null) Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(name);
                        }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ranked_punishments SET active = FALSE WHERE punishment_id = ?")) {
                ps.setString(1, punishmentId);
                ps.executeUpdate();
            }
            if (target != null) {
                List<Punishment> list = cache.get(target);
                if (list != null) {
                    list.removeIf(p -> p.punishmentId().equals(punishmentId));
                    if (list.isEmpty()) cache.remove(target);
                }
                logHistory(conn, punishmentId, target, "REVOKED", staff);
            }
        });
    }

    public boolean isMuted(UUID uuid) {
        return hasActive(uuid, Type.MUTE) || hasActive(uuid, Type.CHATMUTE);
    }

    public boolean isSpectateBanned(UUID uuid) {
        return hasActive(uuid, Type.SPECTATEBAN);
    }

    public boolean isQueueBanned(UUID uuid) {
        return hasActive(uuid, Type.QUEUEBAN);
    }

    private boolean hasActive(UUID uuid, Type type) {
        List<Punishment> list = cache.get(uuid);
        if (list == null) return false;
        return list.stream().anyMatch(p -> p.type() == type && p.isActive());
    }

    public List<Punishment> history(UUID uuid) {
        return services.database().queryAsync(conn -> {
            List<Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM ranked_punishments WHERE uuid = ? ORDER BY start_time DESC")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(read(rs));
                }
            }
            return list;
        }).join();
    }

    private void logHistory(java.sql.Connection conn, String pid, UUID uuid, String action, UUID staff) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO ranked_punishment_history (punishment_id, uuid, action, staff_uuid, created_at)
            VALUES (?,?,?,?,?)
            """)) {
            ps.setString(1, pid);
            ps.setString(2, uuid.toString());
            ps.setString(3, action);
            ps.setString(4, staff == null ? "CONSOLE" : staff.toString());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void sendWebhook(Punishment p) {
        FileConfiguration cfg = services.config().get("punishments.yml");
        if (!cfg.getBoolean("punishments.webhook.enabled", false)) return;
        String url = cfg.getString("punishments.webhook.url", "");
        if (url == null || url.isEmpty()) return;
        String staffName = staffName(p.staffUuid());
        String targetName = Bukkit.getOfflinePlayer(p.uuid()).getName();
        String content = "**Punishment** `" + p.punishmentId() + "`\\nStaff: " + staffName
                + "\\nPlayer: " + targetName + "\\nType: " + p.type()
                + "\\nReason: " + p.reason() + "\\nDuration: " + (p.durationMs() <= 0 ? "Permanent" : (p.durationMs() / 1000) + "s");
        services.discord().sendRaw(url, content);
    }

    private String staffName(UUID uuid) {
        if (uuid == null) return "Console";
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? "Staff" : n;
    }

    private Punishment read(ResultSet rs) throws java.sql.SQLException {
        return new Punishment(
                rs.getString("punishment_id"),
                UUID.fromString(rs.getString("uuid")),
                "CONSOLE".equals(rs.getString("staff_uuid")) ? null : UUID.fromString(rs.getString("staff_uuid")),
                Type.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getLong("duration_ms"),
                rs.getLong("start_time"),
                rs.getLong("end_time"),
                rs.getBoolean("active"),
                rs.getString("evidence_match_id"),
                rs.getString("notes"));
    }

    public record Punishment(String punishmentId, UUID uuid, UUID staffUuid, Type type, String reason,
                             long durationMs, long startTime, long endTime, boolean active,
                             String evidenceMatchId, String notes) {
        public boolean isActive() {
            if (!active) return false;
            return endTime == 0 || System.currentTimeMillis() < endTime;
        }
    }
}
