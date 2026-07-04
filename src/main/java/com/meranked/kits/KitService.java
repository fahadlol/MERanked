package com.meranked.kits;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.util.ItemSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final Map<String, StoredKit> cache = new ConcurrentHashMap<>();
    private KitChecksumService checksumService;

    public KitService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
    }

    public void bindChecksum(KitChecksumService checksumService) {
        this.checksumService = checksumService;
    }

    public void applyKit(Player player, String gamemode) {
        StoredKit kit = getKit(player.getUniqueId(), gamemode);
        // Kit checksum verification: block tampered kits, fall back to default.
        if (checksumService != null && !checksumService.verify(player.getUniqueId(), gamemode, kit)) {
            plugin.getLogger().warning("Blocked tampered kit for " + player.getName() + " (" + gamemode + ")");
            kit = defaultKit(gamemode);
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(kit.armor());
        player.getInventory().setContents(kit.inventory());
        if (kit.enderChest() != null) {
            player.getEnderChest().setContents(padTo(kit.enderChest(), 27));
        }
    }

    public StoredKit getKit(UUID uuid, String gamemode) {
        String key = uuid + ":" + gamemode;
        return cache.computeIfAbsent(key, k -> loadKit(uuid, gamemode));
    }

    /**
     * Warms the kit + checksum caches off the main thread. Called when a match is found so the kit is
     * ready in memory by the time players are teleported (avoids blocking DB reads at match start).
     */
    public void preloadAsync(UUID uuid, String gamemode) {
        String key = uuid + ":" + gamemode;
        if (!cache.containsKey(key)) {
            plugin.tasks().runAsync(() -> cache.computeIfAbsent(key, k -> loadKit(uuid, gamemode)));
        }
        if (checksumService != null) checksumService.preloadAsync(uuid, gamemode);
    }

    public void saveKit(UUID uuid, String gamemode, ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderChest) {
        StoredKit kit = new StoredKit(inventory.clone(), armor.clone(),
                enderChest == null ? new ItemStack[27] : enderChest.clone());
        cache.put(uuid + ":" + gamemode, kit);
        if (checksumService != null) checksumService.store(uuid, gamemode, kit);
        String invData = ItemSerializer.toBase64(inventory);
        String armorData = ItemSerializer.toBase64(armor);
        String enderData = ItemSerializer.toBase64(kit.enderChest());
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_kits (uuid, gamemode, kit_data, ender_chest_data, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                ps.setString(3, invData + "||" + armorData);
                ps.setString(4, enderData);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public boolean hasKit(UUID uuid, String gamemode) {
        StoredKit kit = getKit(uuid, gamemode);
        for (ItemStack item : kit.inventory()) if (item != null) return true;
        for (ItemStack item : kit.armor()) if (item != null) return true;
        return false;
    }

    public void copyKit(UUID from, UUID to, String gamemode) {
        StoredKit kit = getKit(from, gamemode);
        saveKit(to, gamemode, kit.inventory(), kit.armor(), kit.enderChest());
    }

    public void resetKit(UUID uuid, String gamemode) {
        cache.remove(uuid + ":" + gamemode);
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ranked_kits WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                ps.executeUpdate();
            }
        });
    }

    private StoredKit loadKit(UUID uuid, String gamemode) {
        return database.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT kit_data, ender_chest_data FROM ranked_kits WHERE uuid = ? AND gamemode = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String kitData = rs.getString("kit_data");
                        String enderData = rs.getString("ender_chest_data");
                        String[] parts = kitData == null ? new String[0] : kitData.split("\\|\\|", 2);
                        ItemStack[] inv = parts.length > 0 ? ItemSerializer.fromBase64(parts[0], 41) : new ItemStack[41];
                        ItemStack[] armor = parts.length > 1 ? ItemSerializer.fromBase64(parts[1], 4) : new ItemStack[4];
                        ItemStack[] ender = ItemSerializer.fromBase64(enderData, 27);
                        return new StoredKit(inv, armor, ender);
                    }
                }
            }
            return defaultKit(gamemode);
        }).join();
    }

    private StoredKit defaultKit(String gamemode) {
        FileConfiguration kits = configService.get("kits.yml");
        FileConfiguration gamemodes = configService.get("gamemodes.yml");
        String kitId = gamemodes.getString("gamemodes." + gamemode + ".default-kit",
                gamemode.toLowerCase().replace(" ", "_") + "_default");
        Object invObj = kits.get("defaults." + kitId + ".inventory");
        Object armorObj = kits.get("defaults." + kitId + ".armor");

        ItemStack[] inv = toArray(invObj, 41);
        ItemStack[] armor = toArray(armorObj, 4);
        return new StoredKit(inv, armor, new ItemStack[27]);
    }

    @SuppressWarnings("unchecked")
    private ItemStack[] toArray(Object obj, int size) {
        if (obj instanceof java.util.List<?> list) {
            ItemStack[] arr = new ItemStack[Math.max(size, list.size())];
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof ItemStack is) arr[i] = is;
            }
            return arr;
        }
        return new ItemStack[size];
    }

    private ItemStack[] padTo(ItemStack[] items, int size) {
        if (items.length >= size) return items;
        ItemStack[] arr = new ItemStack[size];
        System.arraycopy(items, 0, arr, 0, items.length);
        return arr;
    }

    public void reloadDefaults() {
        cache.clear();
    }

    public record StoredKit(ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderChest) {}
}
