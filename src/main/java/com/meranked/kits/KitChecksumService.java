package com.meranked.kits;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.AlertSeverity;
import com.meranked.util.ItemSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Generates and verifies checksums for saved kits to detect tampering or illegal item injection.
 */
public final class KitChecksumService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;

    public KitChecksumService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public boolean enabled() {
        return services.config().get("kit-checksum.yml").getBoolean("kit-checksum.enabled", true);
    }

    public String compute(KitService.StoredKit kit) {
        FileConfiguration cfg = services.config().get("kit-checksum.yml");
        String algo = cfg.getString("kit-checksum.algorithm", "SHA-256");
        String combined = ItemSerializer.toBase64(kit.inventory())
                + "|" + ItemSerializer.toBase64(kit.armor())
                + "|" + ItemSerializer.toBase64(kit.enderChest());
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(combined.hashCode());
        }
    }

    /** Stores/updates the checksum for a kit (called after a legitimate editor save). */
    public void store(UUID uuid, String gamemode, KitService.StoredKit kit) {
        if (!enabled()) return;
        String checksum = compute(kit);
        services.database().executeAsync(conn -> {
            int version = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT version FROM ranked_kit_checksums WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) version = rs.getInt("version") + 1;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_kit_checksums (uuid, gamemode, checksum, version, updated_at)
                VALUES (?,?,?,?,?)
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                ps.setString(3, checksum);
                ps.setInt(4, version);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    /** Returns true if the kit is valid (matches stored checksum or none stored yet). */
    public boolean verify(UUID uuid, String gamemode, KitService.StoredKit kit) {
        if (!enabled()) return true;
        String expected = loadChecksum(uuid, gamemode);
        if (expected == null) return true; // no baseline yet
        String actual = compute(kit);
        if (expected.equals(actual)) return true;

        FileConfiguration cfg = services.config().get("kit-checksum.yml");
        if (cfg.getBoolean("kit-checksum.alert-on-mismatch", true)) {
            AlertSeverity sev;
            try {
                sev = AlertSeverity.valueOf(cfg.getString("kit-checksum.alert-severity", "HIGH").toUpperCase());
            } catch (IllegalArgumentException ex) {
                sev = AlertSeverity.HIGH;
            }
            services.alerts().createAlert("KIT_CHECKSUM_MISMATCH", sev,
                    "Saved kit hash does not match expected data (" + gamemode + ").", null,
                    java.util.List.of(uuid));
        }
        return !cfg.getBoolean("kit-checksum.block-on-mismatch", true);
    }

    private String loadChecksum(UUID uuid, String gamemode) {
        return services.database().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT checksum FROM ranked_kit_checksums WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("checksum");
                }
            }
            return null;
        }).join();
    }
}
