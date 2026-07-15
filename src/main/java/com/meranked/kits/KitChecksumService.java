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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and verifies checksums for saved kits to detect tampering or illegal item injection.
 */
public final class KitChecksumService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final ConcurrentHashMap<String, String> expectedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> checksumLoads = new ConcurrentHashMap<>();
    private static final String NONE = "\u0000none";

    public KitChecksumService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    private static String key(UUID uuid, String gamemode) {
        return uuid + ":" + gamemode;
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
        expectedCache.put(key(uuid, gamemode), checksum);
        services.database().executeAsync("storeKitChecksum", conn -> {
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

    /** Loads the expected checksum into memory off the main thread (call ahead of a match). */
    public void preloadAsync(UUID uuid, String gamemode) {
        if (!enabled()) return;
        String cacheKey = key(uuid, gamemode);
        if (expectedCache.containsKey(cacheKey)) return;
        loadChecksumAsync(uuid, gamemode);
    }

    /** Returns true if the kit is valid (matches stored checksum or none stored yet). */
    public boolean verify(UUID uuid, String gamemode, KitService.StoredKit kit) {
        if (!enabled()) return true;
        String expected = expectedCache.get(key(uuid, gamemode));
        if (expected == null) {
            loadChecksumAsync(uuid, gamemode);
            return true;
        }
        if (NONE.equals(expected)) return true;
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

    private CompletableFuture<String> loadChecksumAsync(UUID uuid, String gamemode) {
        String cacheKey = key(uuid, gamemode);
        String cached = expectedCache.get(cacheKey);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<String> existing = checksumLoads.get(cacheKey);
        if (existing != null) return existing;

        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> prior = checksumLoads.putIfAbsent(cacheKey, created);
        if (prior != null) return prior;

        services.database().queryAsync("loadKitChecksum", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT checksum FROM ranked_kit_checksums WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("checksum");
                }
            }
            return null;
        }).whenComplete((checksum, error) -> {
            checksumLoads.remove(cacheKey);
            String resolved = checksum == null ? NONE : checksum;
            if (error != null) {
                plugin.getLogger().warning("Failed to load kit checksum for " + uuid + " (" + gamemode + "): "
                        + error.getMessage());
            }
            expectedCache.put(cacheKey, resolved);
            created.complete(resolved);
        });
        return created;
    }
}
