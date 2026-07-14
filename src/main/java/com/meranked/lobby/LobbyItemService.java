package com.meranked.lobby;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LobbyItemService {

    private static final String PDC_KEY = "lobby-item";

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final NamespacedKey itemKey;
    private final Map<LobbyItemType, LobbyItemDefinition> definitions = new EnumMap<>(LobbyItemType.class);

    public LobbyItemService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.itemKey = new NamespacedKey(plugin, PDC_KEY);
        reloadDefinitions();
    }

    public void reloadDefinitions() {
        definitions.clear();
        FileConfiguration config = configService.get("lobby-items.yml");
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            LobbyItemType.fromAction(key).ifPresent(type -> {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section == null) return;
                definitions.put(type, new LobbyItemDefinition(
                        type,
                        section.getBoolean("enabled", true),
                        section.getInt("slot", 0),
                        parseMaterial(section.getString("material", "PAPER")),
                        section.getString("name", "<gold>" + capitalize(type.configKey()) + "</gold>"),
                        section.getStringList("lore"),
                        section.getString("action", type.configKey())
                ));
            });
        }
    }

    public boolean enabled() {
        return configService.get("lobby-items.yml").getBoolean("enabled", true);
    }

    public List<String> enabledItemNames() {
        if (!enabled()) return List.of();
        List<String> names = new ArrayList<>();
        for (LobbyItemDefinition definition : definitions.values()) {
            if (definition.enabled()) names.add(definition.type().configKey());
        }
        return names;
    }

    public void giveLobbyItems(Player player) {
        if (!shouldReceiveLobbyItems(player)) return;
        for (LobbyItemDefinition definition : definitions.values()) {
            if (!definition.enabled()) continue;
            player.getInventory().setItem(definition.slot(), createItem(definition));
        }
    }

    public boolean shouldReceiveLobbyItems(Player player) {
        if (!enabled()) return false;
        ServiceRegistry services = plugin.services();
        if (services == null) return true;
        return !services.matches().isInMatch(player.getUniqueId())
                && !services.kitEditor().isEditing(player.getUniqueId())
                && !services.spectating().isSpectating(player.getUniqueId());
    }

    public ItemStack createItem(LobbyItemType type) {
        LobbyItemDefinition definition = definitions.get(type);
        if (definition == null) {
            return createTaggedItem(type, new ItemStack(Material.PAPER));
        }
        return createItem(definition);
    }

    public Optional<LobbyItemType> identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String value = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        return LobbyItemType.fromAction(value);
    }

    public void handleUse(Player player, LobbyItemType type) {
        ServiceRegistry services = plugin.services();
        switch (type) {
            case QUEUE -> services.gui().openRankedMenu(player);
            case PROFILE -> services.gui().openProfile(player, null);
            case KIT_EDITOR -> services.gui().openKitEditorMenu(player);
            case SETTINGS -> services.gui().openSettings(player);
            case LEADERBOARDS -> services.gui().openLeaderboard(player, null);
        }
    }

    private ItemStack createItem(LobbyItemDefinition definition) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(definition.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : definition.lore()) {
            lore.add(TextUtil.parse(line));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, definition.type().configKey());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTaggedItem(LobbyItemType type, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.configKey());
        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid lobby item material '" + raw + "', using PAPER.");
            return Material.PAPER;
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        String[] parts = value.split("-");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(' ');
            String part = parts[i];
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private record LobbyItemDefinition(
            LobbyItemType type,
            boolean enabled,
            int slot,
            Material material,
            String name,
            List<String> lore,
            String action
    ) {}
}
