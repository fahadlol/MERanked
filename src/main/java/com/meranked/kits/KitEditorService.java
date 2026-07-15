package com.meranked.kits;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.util.LocationUtil;
import com.meranked.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitEditorService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final KitService kitService;
    private final MessageService messages;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingOpens = new ConcurrentHashMap<>();

    public KitEditorService(MERankedPlugin plugin, ConfigService configService,
                            KitService kitService, MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.kitService = kitService;
        this.messages = messages;
    }

    public ItemStack[] getEnderChest(UUID uuid) {
        EditorSession session = sessions.get(uuid);
        return session == null ? new ItemStack[27] : session.enderChest();
    }

    public void setEnderChest(UUID uuid, ItemStack[] contents) {
        EditorSession session = sessions.get(uuid);
        if (session == null) return;
        ItemStack[] target = session.enderChest();
        java.util.Arrays.fill(target, null);
        for (int i = 0; i < Math.min(contents.length, target.length); i++) {
            target[i] = contents[i];
        }
    }

    public void enterEditor(Player player, String gamemode) {
        enterEditorAsync(player, gamemode);
    }

    public void enterEditorAsync(Player player, String gamemode) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) return;
        if (pendingOpens.putIfAbsent(uuid, Boolean.TRUE) != null) return;

        KitService.StoredKit cached = kitService.getCachedKit(uuid, gamemode);
        if (cached != null) {
            pendingOpens.remove(uuid);
            openEditor(player, gamemode, cached);
            return;
        }

        messages.send(player, "kit-editor.loading");
        kitService.getKitAsync(uuid, gamemode).whenComplete((kit, error) ->
                plugin.tasks().runSync(() -> {
                    pendingOpens.remove(uuid);
                    if (!player.isOnline()) return;
                    if (sessions.containsKey(uuid)) return;
                    if (error != null) {
                        messages.send(player, "kit-editor.load-failed");
                        return;
                    }
                    openEditor(player, gamemode, kit);
                }));
    }

    private void openEditor(Player player, String gamemode, KitService.StoredKit kit) {
        if (sessions.containsKey(player.getUniqueId())) return;
        FileConfiguration config = configService.get("config.yml");
        var loc = LocationUtil.fromConfig(config.getConfigurationSection("kit-editor-spawn"));
        if (loc == null) loc = player.getLocation();

        EditorSession session = new EditorSession(gamemode, player.getLocation(), player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone());
        sessions.put(player.getUniqueId(), session);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, player);
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(kit.armor());
        player.getInventory().setContents(kit.inventory());
        if (kit.enderChest() != null) {
            System.arraycopy(kit.enderChest(), 0, session.enderChest(), 0,
                    Math.min(kit.enderChest().length, session.enderChest().length));
        }
        player.teleport(loc);
        player.setGameMode(GameMode.ADVENTURE);

        spawnControls(player, session);
        messages.sendPrefixed(player, "kit-editor.entered", Map.of("gamemode", gamemode));
    }

    public void saveKit(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!validateKit(player)) {
            messages.send(player, "kit-editor.illegal-item");
            return;
        }
        kitService.saveKit(player.getUniqueId(), session.gamemode,
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                session.enderChest());
        messages.sendPrefixed(player, "kit-editor.saved", Map.of("gamemode", session.gamemode));
    }

    public void leaveEditor(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        pendingOpens.remove(player.getUniqueId());
        if (session == null) return;

        session.controlEntities.forEach(org.bukkit.entity.Entity::remove);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        player.getInventory().clear();
        player.getInventory().setContents(session.originalInventory());
        player.getInventory().setArmorContents(session.originalArmor());
        player.teleport(session.returnLocation());
        player.setGameMode(GameMode.SURVIVAL);
        messages.send(player, "kit-editor.left");
    }

    public boolean isEditing(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public Optional<String> editingGamemode(UUID uuid) {
        EditorSession session = sessions.get(uuid);
        return session == null ? Optional.empty() : Optional.of(session.gamemode());
    }

    private boolean validateKit(Player player) {
        FileConfiguration config = configService.get("kit-editor.yml");
        List<String> illegal = config.getStringList("illegal-items");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && illegal.contains(item.getType().name())) return false;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && illegal.contains(item.getType().name())) return false;
        }
        return true;
    }

    private void spawnControls(Player player, EditorSession session) {
        FileConfiguration config = configService.get("kit-editor.yml");
        if (!config.getBoolean("controls.use-text-display", true)) return;

        var base = player.getLocation().clone().add(2, 0, 0);
        session.controlEntities.add(createControl(base, "<green><bold>Save Kit</bold></green>", "save"));
        session.controlEntities.add(createControl(base.clone().add(0, 0, 1.5), "<yellow><bold>Revert Changes</bold></yellow>", "revert"));
        session.controlEntities.add(createControl(base.clone().add(0, 0, 3), "<aqua><bold>Armor Trims</bold></aqua>", "trims"));
        session.controlEntities.add(createControl(base.clone().add(0, 0, 4.5), "<light_purple><bold>Ender Chest</bold></light_purple>", "enderchest"));
        session.controlEntities.add(createControl(base.clone().add(0, 0, 6), "<red><bold>Leave Editor</bold></red>", "leave"));
    }

    private org.bukkit.entity.Entity createControl(org.bukkit.Location loc, String label, String action) {
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.text(TextUtil.parse(label));
        display.setBillboard(Display.Billboard.CENTER);
        display.addScoreboardTag("meranked-kit-control");
        display.addScoreboardTag("meranked-action-" + action);
        Interaction interaction = (Interaction) loc.getWorld().spawnEntity(loc, EntityType.INTERACTION);
        interaction.setInteractionWidth(1.5f);
        interaction.setInteractionHeight(1.5f);
        interaction.addScoreboardTag("meranked-kit-control");
        interaction.addScoreboardTag("meranked-action-" + action);
        return display;
    }

    public void handleControlClick(Player player, String action) {
        switch (action) {
            case "save" -> saveKit(player);
            case "revert" -> {
                EditorSession session = sessions.get(player.getUniqueId());
                if (session == null) return;
                kitService.getKitAsync(player.getUniqueId(), session.gamemode()).thenAccept(kit ->
                        plugin.tasks().runSync(() -> {
                            if (!player.isOnline() || sessions.get(player.getUniqueId()) == null) return;
                            player.getInventory().setContents(kit.inventory());
                            player.getInventory().setArmorContents(kit.armor());
                            messages.send(player, "kit-editor.reverted");
                        }));
            }
            case "trims" -> plugin.services().gui().openTrimEditor(player);
            case "enderchest" -> {
                EditorSession session = sessions.get(player.getUniqueId());
                if (session != null) plugin.services().gui().openEnderChestEditor(player, session.gamemode());
            }
            case "leave" -> leaveEditor(player);
        }
    }

    public ItemStack createEditorItem() {
        return plugin.services().lobbyItems().createItem(com.meranked.lobby.LobbyItemType.KIT_EDITOR);
    }

    private record EditorSession(String gamemode, org.bukkit.Location returnLocation,
                                 ItemStack[] originalInventory, ItemStack[] originalArmor,
                                 List<org.bukkit.entity.Entity> controlEntities,
                                 ItemStack[] enderChest) {
        private EditorSession(String gamemode, org.bukkit.Location returnLocation,
                              ItemStack[] originalInventory, ItemStack[] originalArmor) {
            this(gamemode, returnLocation, originalInventory, originalArmor, new ArrayList<>(), new ItemStack[27]);
        }
    }
}
