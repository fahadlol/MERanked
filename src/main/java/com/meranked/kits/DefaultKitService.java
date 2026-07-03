package com.meranked.kits;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultKitService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final KitService kitService;
    private final Map<UUID, PendingDefaultKit> pending = new ConcurrentHashMap<>();

    public DefaultKitService(MERankedPlugin plugin, ConfigService configService, KitService kitService) {
        this.plugin = plugin;
        this.configService = configService;
        this.kitService = kitService;
    }

    public void beginSetDefault(Player admin, String gamemode) {
        ItemStack[] inv = admin.getInventory().getContents().clone();
        ItemStack[] armor = admin.getInventory().getArmorContents().clone();
        pending.put(admin.getUniqueId(), new PendingDefaultKit(gamemode, inv, armor));
    }

    public PendingDefaultKit getPending(UUID uuid) {
        return pending.get(uuid);
    }

    public void confirm(Player admin) {
        PendingDefaultKit p = pending.remove(admin.getUniqueId());
        if (p == null) return;
        saveDefaultKit(p.gamemode(), p.inventory(), p.armor());
        kitService.reloadDefaults();
        admin.sendMessage(TextUtil.parse("<green>Default kit saved for <gold>" + p.gamemode() + "</gold>.</green>"));
    }

    public void cancel(Player admin) {
        pending.remove(admin.getUniqueId());
        admin.sendMessage(TextUtil.parse("<red>Default kit change cancelled.</red>"));
    }

    public void saveDefaultKit(String gamemode, ItemStack[] inventory, ItemStack[] armor) {
        FileConfiguration kits = configService.get("kits.yml");
        FileConfiguration gamemodes = configService.get("gamemodes.yml");
        String kitId = gamemodes.getString("gamemodes." + gamemode + ".default-kit",
                gamemode.toLowerCase().replace(" ", "_") + "_default");
        kits.set("defaults." + kitId + ".gamemode", gamemode);
        kits.set("defaults." + kitId + ".inventory", inventory);
        kits.set("defaults." + kitId + ".armor", armor);
        configService.save("kits.yml", kits);
        kitService.reloadDefaults();
    }

    public ItemStack previewItem(ItemStack original, String label) {
        ItemStack item = original == null || original.getType() == Material.AIR
                ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE) : original.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse("<gray>" + label + "</gray>"));
        item.setItemMeta(meta);
        return item;
    }

    public record PendingDefaultKit(String gamemode, ItemStack[] inventory, ItemStack[] armor) {}
}
