package com.meranked.gui;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.kits.DefaultKitService;
import com.meranked.model.RankedProfile;
import com.meranked.model.StaffAlert;
import com.meranked.rating.LeaderboardService;
import com.meranked.rating.TierService;
import com.meranked.settings.PlayerSettings;
import com.meranked.staff.WatchlistService;
import com.meranked.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final ConfigService configService;
    private final TierService tierService;
    private final Map<UUID, GuiSession> openSessions = new ConcurrentHashMap<>();

    public GuiManager(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
        this.configService = services.config();
        this.tierService = services.tiers();
    }

    public GuiSession session(Player player) {
        return openSessions.get(player.getUniqueId());
    }

    public void setSession(Player player, GuiSession session) {
        openSessions.put(player.getUniqueId(), session);
    }

    public void clearSession(Player player) {
        openSessions.remove(player.getUniqueId());
    }

    public void openRankedMenu(Player player) {
        FileConfiguration guis = configService.get("guis.yml");
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parse(guis.getString("ranked-menu.title")));
        int slot = 10;
        for (String mode : services.profiles().enabledGamemodes()) {
            if (slot > 34) break;
            Material icon = Material.NETHER_STAR;
            try {
                icon = Material.valueOf(configService.get("gamemodes.yml").getString("gamemodes." + mode + ".icon", "NETHER_STAR"));
            } catch (Exception ignored) {}
            RankedProfile p = services.profiles().getProfile(player.getUniqueId(), mode);
            inv.setItem(slot++, item(icon, "<gold>" + mode + "</gold>",
                    "Tier: " + services.placements().displayTier(p),
                    "<yellow>Click to queue</yellow>"));
        }
        inv.setItem(40, item(Material.COMPARATOR, "<gold>Settings</gold>", "Open player settings"));
        inv.setItem(41, item(Material.BOOK, "<gold>Profile</gold>", "View your ranked profile"));
        inv.setItem(42, item(Material.GOLD_BLOCK, "<gold>Leaderboards</gold>", "Top players"));
        setSession(player, GuiSession.of(GuiType.RANKED_MENU));
        player.openInventory(inv);
    }

    public void openProfile(Player player, String gamemode) {
        if (gamemode == null || gamemode.isEmpty()) gamemode = services.profiles().enabledGamemodes().get(0);
        RankedProfile p = services.profiles().getProfile(player.getUniqueId(), gamemode);
        var rp = services.profiles().getPlayer(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gradient:#D6B36A:#7C3AED><bold>Profile</bold></gradient>"));
        inv.setItem(4, item(Material.PLAYER_HEAD, "<gold>" + player.getName() + "</gold>",
                "Region: " + (rp.regionHidden() ? "Hidden" : rp.region())));
        inv.setItem(20, item(Material.IRON_SWORD, "Current", p.tier() + " — " + Math.round(p.rating())));
        inv.setItem(22, item(Material.GOLDEN_SWORD, "Peak", p.peakTier() + " — " + Math.round(p.peakRating())));
        inv.setItem(24, item(Material.DIAMOND_SWORD, "Season Peak", p.seasonPeakTier() + " — " + Math.round(p.seasonPeakRating())));
        inv.setItem(30, item(Material.PAPER, "Record", p.wins() + "W / " + p.losses() + "L"));
        inv.setItem(32, item(Material.EXPERIENCE_BOTTLE, "Confidence", tierService.getConfidenceLabel(p.ratingDeviation())));
        if (p.inPlacements(services.profiles().placementRequired(gamemode))) {
            inv.setItem(31, item(Material.BARRIER, "Placements", p.placementPlayed() + "/" + services.profiles().placementRequired(gamemode)));
        }
        setSession(player, GuiSession.of(GuiType.PROFILE, gamemode));
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        PlayerSettings s = services.settings().get(player.getUniqueId());
        FileConfiguration cfg = configService.get("settings.yml");
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("gui-size", 45),
                TextUtil.parse(cfg.getString("gui-title")));
        inv.setItem(cfg.getInt("options.messages.slot", 10),
                toggleItem(Material.WRITABLE_BOOK, "Private Messages", s.messagesEnabled()));
        inv.setItem(cfg.getInt("options.spectate-requests.slot", 12),
                toggleItem(Material.ENDER_EYE, "Spectate Requests", s.spectateRequestsEnabled()));
        inv.setItem(cfg.getInt("options.queue-notifications.slot", 14),
                toggleItem(Material.BELL, "Queue Notifications", s.queueNotifications()));
        inv.setItem(cfg.getInt("options.region-display.slot", 16),
                toggleItem(Material.WHITE_BANNER, "Region Visible", !s.regionHidden()));
        setSession(player, GuiSession.of(GuiType.SETTINGS));
        player.openInventory(inv);
    }

    public void openStaffAlerts(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gradient:#D6B36A:#7C3AED><bold>Staff Alerts</bold></gradient>"));
        int slot = 0;
        for (StaffAlert alert : services.alerts().recentAlerts()) {
            if (slot >= 45) break;
            inv.setItem(slot++, item(Material.PAPER, "<red>" + alert.type() + "</red>",
                    "Severity: " + alert.severity(),
                    "Players: " + alert.players(),
                    "Match: " + alert.matchId(),
                    "Reason: " + alert.reason(),
                    "<yellow>Click to inspect</yellow>"));
        }
        setSession(staff, GuiSession.of(GuiType.STAFF_ALERTS));
        staff.openInventory(inv);
    }

    public void openAlertDetail(Player staff, StaffAlert alert) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse("<red>Alert: " + alert.type() + "</red>"));
        inv.setItem(10, item(Material.BOOK, "Info", alert.reason()));
        inv.setItem(11, item(Material.NAME_TAG, "Players", alert.players()));
        inv.setItem(12, item(Material.MAP, "Match", alert.matchId().isEmpty() ? "N/A" : alert.matchId()));
        if (!alert.matchId().isEmpty()) {
            inv.setItem(13, item(Material.TNT, "<red>Rollback Match</red>", "Undo rating changes"));
            inv.setItem(14, item(Material.SPYGLASS, "<gold>View Replay</gold>", "Open match replay"));
        }
        inv.setItem(15, item(Material.LIME_DYE, "<green>Resolve</green>", "Mark resolved"));
        inv.setItem(16, item(Material.PLAYER_HEAD, "<yellow>Watch Player</yellow>", "Add to watchlist"));
        setSession(staff, new GuiSession(GuiType.STAFF_ALERT_DETAIL, alert.alertId(), alert.matchId()));
        staff.openInventory(inv);
    }

    public void openWatchlist(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("Staff Watchlist"));
        int i = 0;
        for (WatchlistService.WatchEntry e : services.watchlist().all()) {
            if (i >= 45) break;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid()));
            meta.displayName(TextUtil.parse("<gold>" + e.name() + "</gold>"));
            meta.lore(List.of(
                    TextUtil.parse("<gray>Reason: " + e.reason() + "</gray>"),
                    TextUtil.parse("<gray>By: " + e.addedBy() + "</gray>")
            ));
            skull.setItemMeta(meta);
            inv.setItem(i++, skull);
        }
        setSession(staff, GuiSession.of(GuiType.WATCHLIST));
        staff.openInventory(inv);
    }

    public void openRollbackConfirm(Player staff, String matchId) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse("<red>Confirm Rollback</red>"));
        inv.setItem(11, item(Material.LIME_CONCRETE, "<green>Confirm</green>", "Rollback " + matchId));
        inv.setItem(15, item(Material.RED_CONCRETE, "<red>Cancel</red>", "Go back"));
        setSession(staff, GuiSession.of(GuiType.ROLLBACK_CONFIRM, matchId));
        staff.openInventory(inv);
    }

    public void openReplay(Player viewer, String matchId) {
        plugin.tasks().runAsync(() -> {
            Map<String, Object> data = services.replays().loadReplay(matchId);
            int eventCount = services.replays().loadTimeline(matchId).size();
            plugin.tasks().runSync(() -> renderReplay(viewer, matchId, data, eventCount));
        });
    }

    private void renderReplay(Player viewer, String matchId, Map<String, Object> data, int eventCount) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("Replay — " + matchId));
        if (data == null) {
            inv.setItem(22, item(Material.BARRIER, "<red>Replay not found</red>", matchId));
            setSession(viewer, GuiSession.of(GuiType.REPLAY, matchId));
            viewer.openInventory(inv);
            return;
        }
        String gamemode = String.valueOf(data.getOrDefault("gamemode", "?"));
        String arena = String.valueOf(data.getOrDefault("arena", "?"));
        String winner = resolveName(String.valueOf(data.getOrDefault("winner", "")));
        String loser = resolveName(String.valueOf(data.getOrDefault("loser", "")));
        Object durObj = data.get("duration");
        long duration = durObj instanceof Number n ? n.longValue() : 0L;
        inv.setItem(10, item(Material.BOOK, "Match Info", "Gamemode: " + gamemode, "Arena: " + arena,
                "Duration: " + (duration / 1000) + "s"));
        inv.setItem(12, item(Material.DIAMOND_SWORD, "<green>Winner</green>", winner));
        inv.setItem(14, item(Material.IRON_SWORD, "<red>Loser</red>", loser));
        inv.setItem(16, item(Material.CLOCK, "Combat Timeline", eventCount + " events", "<yellow>Click to print</yellow>"));
        inv.setItem(30, item(Material.PAPER, "Rating Change",
                Boolean.TRUE.equals(data.get("noRating")) ? "<red>No rating applied</red>" : "Rated match"));
        setSession(viewer, GuiSession.of(GuiType.REPLAY, matchId));
        viewer.openInventory(inv);
    }

    private String resolveName(String uuid) {
        if (uuid == null || uuid.isEmpty()) return "?";
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return name == null ? uuid : name;
        } catch (Exception ex) {
            return uuid;
        }
    }

    public void openKitAudit(Player staff, UUID target, String gamemode) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("Kit Audit — " + Bukkit.getOfflinePlayer(target).getName()));
        inv.setItem(10, item(Material.CHEST, "Inventory", "View inventory kit"));
        inv.setItem(12, item(Material.IRON_CHESTPLATE, "Armor", "View armor"));
        inv.setItem(14, item(Material.SHULKER_BOX, "Shulkers", "Validation"));
        inv.setItem(16, item(Material.ENDER_CHEST, "Ender Chest", "View ender chest"));
        inv.setItem(28, item(Material.PAPER, "Validation", services.kitValidation().validate(target, gamemode).summary()));
        inv.setItem(30, item(Material.TNT, "Reset Kit", "Reset player kit"));
        inv.setItem(32, item(Material.EMERALD, "Copy Kit", "Copy to another player"));
        setSession(staff, new GuiSession(GuiType.KIT_AUDIT, target.toString(), gamemode));
        staff.openInventory(inv);
    }

    public void openDefaultKitConfirm(Player admin, DefaultKitService.PendingDefaultKit pending) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("Confirm Default Kit — " + pending.gamemode()));
        int slot = 0;
        for (ItemStack stack : pending.inventory()) {
            if (slot >= 36) break;
            if (stack != null && stack.getType() != Material.AIR) inv.setItem(slot++, stack.clone());
        }
        inv.setItem(45, item(Material.LIME_CONCRETE, "<green><bold>Confirm</bold></green>", "Save as default kit"));
        inv.setItem(53, item(Material.RED_CONCRETE, "<red><bold>Cancel</bold></red>", "Discard changes"));
        setSession(admin, GuiSession.of(GuiType.DEFAULT_KIT_CONFIRM, pending.gamemode()));
        admin.openInventory(inv);
    }

    public void openLeaderboard(Player player, String gamemode) {
        if (gamemode == null) gamemode = services.profiles().enabledGamemodes().get(0);
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Leaderboard — " + gamemode + "</gold>"));
        int slot = 0;
        for (LeaderboardService.LeaderboardEntry e : services.leaderboard().getTop(gamemode, 10)) {
            inv.setItem(slot++, item(Material.GOLD_INGOT,
                    "#" + e.rank() + " " + e.username() + " [" + e.region() + "]",
                    e.tier() + " — " + Math.round(e.rating()),
                    e.wins() + "W / " + e.losses() + "L"));
        }
        setSession(player, GuiSession.of(GuiType.LEADERBOARD, gamemode));
        player.openInventory(inv);
    }

    public void openPlacementRecap(Player player, RankedProfile profile) {
        FileConfiguration guis = configService.get("guis.yml");
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse(guis.getString("placement-recap.title", "Placement Recap")));
        inv.setItem(10, item(Material.GOLD_INGOT, "Final Tier", profile.tier()));
        inv.setItem(12, item(Material.PAPER, "Record", profile.placementWins() + "W - " + profile.placementLosses() + "L"));
        inv.setItem(14, item(Material.DIAMOND_SWORD, "Best Win", profile.bestWinOpponent() == null ? "N/A" : "vs " + profile.bestWinOpponent()));
        inv.setItem(16, item(Material.EXPERIENCE_BOTTLE, "Confidence", tierService.getConfidenceLabel(profile.ratingDeviation())));
        inv.setItem(22, item(Material.LIME_DYE, "Continue", "Close"));
        setSession(player, GuiSession.of(GuiType.PLACEMENT_RECAP));
        player.openInventory(inv);
    }

    public void openTrimEditor(Player player) {
        FileConfiguration cfg = configService.get("kit-editor.yml");
        String material = trimMaterials(cfg).get(0);
        String pattern = trimPatterns(cfg).get(0);
        renderTrimEditor(player, material, pattern);
    }

    public void renderTrimEditor(Player player, String material, String pattern) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse("<gold>Armor Trim Editor</gold>"));
        inv.setItem(11, item(Material.DIAMOND, "Trim Material", "Current: <yellow>" + material + "</yellow>", "Click to cycle"));
        inv.setItem(13, item(Material.NETHERITE_HELMET, "Trim Pattern", "Current: <yellow>" + pattern + "</yellow>", "Click to cycle"));
        inv.setItem(15, item(Material.LIME_DYE, "Apply Trim", "Apply to all worn armor"));
        inv.setItem(16, item(Material.BARRIER, "Remove Trim", "Clear trims"));
        setSession(player, new GuiSession(GuiType.TRIM_EDITOR, material, pattern));
        player.openInventory(inv);
    }

    public java.util.List<String> trimMaterials(FileConfiguration cfg) {
        java.util.List<String> list = cfg.getStringList("armor-trims.allow-trim-materials");
        if (list.isEmpty()) list = java.util.List.of("quartz", "iron", "gold", "diamond", "netherite", "redstone",
                "copper", "emerald", "lapis", "amethyst");
        return list.stream().map(String::toUpperCase).toList();
    }

    public java.util.List<String> trimPatterns(FileConfiguration cfg) {
        java.util.List<String> list = cfg.getStringList("armor-trims.allow-trim-patterns");
        if (list.isEmpty()) list = java.util.List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex",
                "tide", "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host");
        return list.stream().map(String::toUpperCase).toList();
    }

    public void openEnderChestEditor(Player player, String gamemode) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse("<dark_purple>Ender Chest Kit</dark_purple>"));
        ItemStack[] current = services.kitEditor().getEnderChest(player.getUniqueId());
        for (int i = 0; i < Math.min(current.length, 27); i++) {
            if (current[i] != null) inv.setItem(i, current[i]);
        }
        setSession(player, GuiSession.of(GuiType.ENDER_CHEST_EDITOR, gamemode));
        player.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextUtil.parse(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(TextUtil.parse("<gray>" + l + "</gray>"));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack toggleItem(Material mat, String name, boolean enabled) {
        return item(mat, name, enabled ? "<green>Enabled</green>" : "<red>Disabled</red>", "Click to toggle");
    }
}
