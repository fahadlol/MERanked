package com.meranked.gui;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.kits.DefaultKitService;
import com.meranked.matches.FairnessDashboardService;
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

    public void openKitEditorMenu(Player player) {
        FileConfiguration guis = configService.get("guis.yml");
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parse(guis.getString("kit-editor-menu.title",
                "<gradient:#D6B36A:#7C3AED><bold>Kit Editor</bold></gradient>")));
        int slot = 10;
        for (String mode : services.profiles().enabledGamemodes()) {
            if (slot > 34) break;
            Material icon = Material.CHEST;
            try {
                icon = Material.valueOf(configService.get("gamemodes.yml").getString("gamemodes." + mode + ".icon", "CHEST"));
            } catch (Exception ignored) {}
            inv.setItem(slot++, item(icon, "<gold>" + mode + "</gold>", "<yellow>Click to edit kit</yellow>"));
        }
        setSession(player, GuiSession.of(GuiType.KIT_EDITOR_MENU));
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
        } else {
            String bar = TextUtil.plain(TextUtil.parse(services.rankProgress().buildBar(p)));
            inv.setItem(31, item(Material.EMERALD, "Progress",
                    bar + " " + Math.round(p.rating()) + "/" + services.rankProgress().nextTierId(p),
                    "Rating to next: " + services.rankProgress().ratingToNext(p)));
            if (services.rankProgress().atDemotionRisk(p)) {
                inv.setItem(40, item(Material.REDSTONE, "<red>Demotion Risk: Yes</red>",
                        "Current Rating: " + Math.round(p.rating()),
                        "Safe Rating: " + services.rankProgress().safeRating(p) + "+"));
            }
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
        if (services.replays().visualReplayEnabled()) {
            inv.setItem(28, item(Material.SPYGLASS, "<gold>Visual Replay</gold>",
                    eventCount + " events", "<yellow>Click to open</yellow>"));
        }
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

    // ---- Ranked Identity Card ----

    public void openIdentityCard(Player viewer, UUID target, String targetName) {
        FileConfiguration cfg = configService.get("identity-card.yml");
        var rp = services.profiles().getPlayer(target);
        String region = rp == null ? "Other" : (rp.regionHidden() ? "Hidden" : rp.region());
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parse(
                cfg.getString("title", "<gold><player></gold>").replace("<player>", targetName).replace("<region>", region)));

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
        meta.displayName(TextUtil.parse("<gold>" + targetName + " [" + region + "]</gold>"));
        skull.setItemMeta(meta);
        inv.setItem(4, skull);

        // per-gamemode tiers
        int slot = 19;
        RankedProfile best = null;
        int totalWins = 0, totalLosses = 0, bestStreak = 0;
        String mainTier = "#0";
        for (String mode : services.profiles().enabledGamemodes()) {
            RankedProfile p = services.profiles().getProfile(target, mode);
            if (!p.ranked()) continue;
            if (slot <= 25) {
                inv.setItem(slot++, item(Material.NETHER_STAR, "<gold>" + mode + "</gold>",
                        p.tier() + " — " + Math.round(p.rating())));
            }
            totalWins += p.wins();
            totalLosses += p.losses();
            bestStreak = Math.max(bestStreak, p.winStreak());
            if (best == null || p.rating() > best.rating()) { best = p; mainTier = p.tier(); }
        }
        int rate = totalWins + totalLosses == 0 ? 0 : Math.round(100f * totalWins / (totalWins + totalLosses));
        String bestMode = best == null ? "N/A" : best.gamemode();
        int seasonRank = best == null ? 0 : services.leaderboard().getRankCached(target, bestMode);

        inv.setItem(29, item(Material.GOLD_INGOT, "<gold>Main Tier</gold>", mainTier));
        inv.setItem(30, item(Material.DIAMOND, "<gold>Peak Tier</gold>", best == null ? "N/A" : best.peakTier()));
        inv.setItem(31, item(Material.NETHERITE_SWORD, "<gold>Best Gamemode</gold>", bestMode));
        inv.setItem(32, item(Material.EXPERIENCE_BOTTLE, "<gold>Confidence</gold>",
                best == null ? "N/A" : tierService.getConfidenceLabel(best.ratingDeviation())));
        inv.setItem(33, item(Material.PAPER, "<gold>Season Rank</gold>", seasonRank <= 0 ? "Unranked" : "#" + seasonRank));
        inv.setItem(38, item(Material.BOOK, "<gold>Win Rate</gold>", rate + "%"));
        inv.setItem(42, item(Material.BLAZE_POWDER, "<gold>Current Streak</gold>", bestStreak + "W"));
        setSession(viewer, GuiSession.of(GuiType.IDENTITY_CARD, target.toString()));
        viewer.openInventory(inv);
    }

    // ---- Fairness / Transparency Dashboard ----

    public void openFairnessDashboard(Player player) {
        if (!services.fairnessDashboard().enabled()) return;
        FileConfiguration cfg = configService.get("fairness-dashboard.yml");
        int size = cfg.getInt("fairness-dashboard.size", 45);
        String title = cfg.getString("fairness-dashboard.title", "Match Fairness");

        plugin.tasks().runAsync(() -> {
            var history = services.fairnessDashboard().recentMatches(player.getUniqueId());
            double mmrGap = 0;
            String mode = services.profiles().enabledGamemodes().get(0);
            var profile = services.profiles().getProfile(player.getUniqueId(), mode);
            if (profile.ranked()) mmrGap = services.hiddenMmr().mmrGap(profile);

            double gapFinal = mmrGap;
            plugin.tasks().runSync(() -> {
                Inventory inv = Bukkit.createInventory(null, size, TextUtil.parse(title));
                inv.setItem(4, item(Material.BOOK, "<gold>Fairness Pledge</gold>",
                        cfg.getString("fairness-dashboard.pledge", "").split("\n")));
                inv.setItem(13, item(Material.COMPASS, "<gold>Hidden MMR Gap</gold>",
                        "Gap: " + Math.round(gapFinal) + " rating",
                        "Positive = playing above visible tier"));
                int slot = 19;
                for (FairnessDashboardService.FairnessEntry e : history) {
                    if (slot > 34) break;
                    inv.setItem(slot++, item(Material.PAPER,
                            "<gray>" + e.gamemode() + " — " + e.quality() + "%</gray>",
                            e.reason() == null ? "" : e.reason(),
                            "Rating gap: " + Math.round(e.ratingDiff()),
                            "Ping gap: " + e.pingDiff() + "ms"));
                }
                inv.setItem(size - 5, item(Material.RED_CONCRETE, "<red>Close</red>", "Close"));
                setSession(player, GuiSession.of(GuiType.FAIRNESS_DASHBOARD));
                player.openInventory(inv);
            });
        });
    }

    // ---- Staff Command Center ----

    public void openStaffCenter(Player staff) {
        FileConfiguration cfg = configService.get("staff-center.yml");
        int size = cfg.getInt("staff-center.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parse(
                cfg.getString("staff-center.title", "MERanked Staff Center")));
        inv.setItem(cfg.getInt("staff-center.items.live-matches", 10),
                item(Material.DIAMOND_SWORD, "<gold>Live Matches</gold>", services.matches().liveMatches().size() + " active"));
        inv.setItem(cfg.getInt("staff-center.items.staff-alerts", 12),
                item(Material.BELL, "<gold>Staff Alerts</gold>", services.alerts().recentAlerts().size() + " recent"));
        inv.setItem(cfg.getInt("staff-center.items.watchlist", 14),
                item(Material.ENDER_EYE, "<gold>Watchlist</gold>", "View watched players"));
        inv.setItem(cfg.getInt("staff-center.items.suspicious-players", 16),
                item(Material.SPYGLASS, "<gold>Suspicious Players</gold>", "Flagged accounts"));
        inv.setItem(cfg.getInt("staff-center.items.broken-arenas", 28),
                item(Material.BARRIER, "<gold>Broken Arenas</gold>", "Disabled arenas"));
        inv.setItem(cfg.getInt("staff-center.items.kit-audits", 30),
                item(Material.CHEST, "<gold>Kit Audits</gold>", "Inspect kits"));
        inv.setItem(cfg.getInt("staff-center.items.rollback-logs", 32),
                item(Material.CLOCK, "<gold>Rollback Logs</gold>", "Recent rollbacks"));
        inv.setItem(cfg.getInt("staff-center.items.evidence-bundles", 34),
                item(Material.WRITABLE_BOOK, "<gold>Evidence Bundles</gold>", "Generated bundles"));
        inv.setItem(cfg.getInt("staff-center.items.ranked-bans", 40),
                item(Material.IRON_BARS, "<gold>Ranked Bans</gold>", "Active bans"));
        inv.setItem(cfg.getInt("staff-center.items.close", 49),
                item(Material.RED_CONCRETE, "<red>Close</red>", "Close menu"));
        setSession(staff, GuiSession.of(GuiType.STAFF_CENTER));
        staff.openInventory(inv);
    }

    public void openStaffPanel(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gradient:#D6B36A:#7C3AED><bold>Staff Panel</bold></gradient>"));
        boolean connected = services.discordBridge().isConnected();
        inv.setItem(4, item(Material.REPEATER, connected ? "<green>Bridge Connected</green>" : "<red>Bridge Offline</red>",
                "Queued: " + services.discordBridge().queueSize()));
        inv.setItem(10, item(Material.COMPASS, "<gold>Staff On Duty</gold>",
                services.staffDuty().onDutyCount() + " on duty"));
        inv.setItem(12, item(Material.PAPER, "<gold>Active Reports</gold>",
                services.reports().openReports().size() + " open"));
        inv.setItem(14, item(Material.IRON_BARS, "<gold>Recent Punishments</gold>", "Use /history"));
        inv.setItem(16, item(Material.REDSTONE, "<gold>Suspicion Alerts</gold>",
                services.alerts().recentAlerts().size() + " recent"));
        inv.setItem(28, item(Material.IRON_SWORD, "<gold>Match Alerts</gold>",
                services.matches().liveMatches().size() + " live"));
        inv.setItem(30, item(Material.BARRIER, "<gold>Arena Issues</gold>", "Broken/disabled arenas"));
        inv.setItem(32, item(Material.WRITABLE_BOOK, "<gold>Staff Notes</gold>", "Use /staffnote"));
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Close</red>", "Close panel"));
        setSession(staff, GuiSession.of(GuiType.STAFF_PANEL));
        staff.openInventory(inv);
    }

    public void openBrokenArenas(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<red>Broken / Disabled Arenas</red>"));
        int slot = 0;
        for (var arena : services.arenas().allArenas()) {
            if (slot >= 45) break;
            if (!arena.broken() && arena.enabled()) continue;
            String status = arena.broken() ? "Broken" : "Disabled";
            String reason = arena.brokenReason() == null ? "" : arena.brokenReason();
            inv.setItem(slot++, item(Material.BARRIER, "<red>" + arena.name() + "</red>",
                    status, reason.isEmpty() ? "No reason recorded" : reason));
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.LIME_DYE, "<green>All arenas healthy</green>", "No issues found"));
        }
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Return to staff center"));
        setSession(staff, GuiSession.of(GuiType.BROKEN_ARENAS));
        staff.openInventory(inv);
    }

    public void openKitAuditPlayerPicker(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Kit Audit — Select Player</gold>"));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(TextUtil.parse("<gold>" + online.getName() + "</gold>"));
            meta.lore(List.of(TextUtil.parse("<gray>Click to pick gamemode</gray>")));
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.BARRIER, "<red>No players online</red>", "Try again later"));
        }
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Return to staff center"));
        setSession(staff, GuiSession.of(GuiType.KIT_AUDIT_PLAYER));
        staff.openInventory(inv);
    }

    public void openKitAuditGamemodePicker(Player staff, UUID target) {
        String name = Bukkit.getOfflinePlayer(target).getName();
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("Kit Audit — " + (name == null ? target : name)));
        int slot = 10;
        for (String mode : services.profiles().enabledGamemodes()) {
            if (slot > 34) break;
            inv.setItem(slot++, item(Material.CHEST, "<gold>" + mode + "</gold>", "Audit kit for this gamemode"));
        }
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Pick another player"));
        setSession(staff, GuiSession.of(GuiType.KIT_AUDIT_GAMEMODE, target.toString()));
        staff.openInventory(inv);
    }

    public void openRollbackLogs(Player staff) {
        plugin.tasks().runAsync(() -> {
            var logs = services.rollback().recentLogs(45);
            plugin.tasks().runSync(() -> {
                Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Rollback Logs</gold>"));
                int slot = 0;
                for (var log : logs) {
                    if (slot >= 45) break;
                    String staffName = log.staffUuid();
                    try {
                        String resolved = Bukkit.getOfflinePlayer(UUID.fromString(log.staffUuid())).getName();
                        if (resolved != null) staffName = resolved;
                    } catch (IllegalArgumentException ignored) {}
                    inv.setItem(slot++, item(Material.CLOCK, "<gray>" + log.matchIds() + "</gray>",
                            "By: " + (staffName == null ? log.staffUuid() : staffName),
                            "Reason: " + log.reason(),
                            formatWhen(log.createdAt())));
                }
                if (slot == 0) {
                    inv.setItem(22, item(Material.PAPER, "<gray>No rollbacks yet</gray>", "Nothing recorded"));
                }
                inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Return to staff center"));
                setSession(staff, GuiSession.of(GuiType.ROLLBACK_LOGS));
                staff.openInventory(inv);
            });
        });
    }

    public void openEvidenceBundles(Player staff) {
        plugin.tasks().runAsync(() -> {
            var bundles = services.evidence().recentBundles(45);
            plugin.tasks().runSync(() -> {
                Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Evidence Bundles</gold>"));
                int slot = 0;
                for (var bundle : bundles) {
                    if (slot >= 45) break;
                    inv.setItem(slot++, item(Material.WRITABLE_BOOK, "<gold>" + bundle.bundleId() + "</gold>",
                            bundle.targetType() + ": " + bundle.targetId(),
                            bundle.reason(),
                            "Suspicion: " + bundle.suspicion() + "/100",
                            formatWhen(bundle.createdAt())));
                }
                if (slot == 0) {
                    inv.setItem(22, item(Material.PAPER, "<gray>No bundles yet</gray>", "Generate with /ranked evidence"));
                }
                inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Return to staff center"));
                setSession(staff, GuiSession.of(GuiType.EVIDENCE_BUNDLES));
                staff.openInventory(inv);
            });
        });
    }

    public void openRankedBans(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Active Ranked Bans</gold>"));
        int slot = 0;
        for (var ban : services.bans().activeBans()) {
            if (slot >= 45) break;
            String name = Bukkit.getOfflinePlayer(ban.uuid()).getName();
            String expires = ban.expiresAt() <= 0 ? "Permanent"
                    : "Until " + formatWhen(ban.expiresAt());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ban.uuid()));
            meta.displayName(TextUtil.parse("<red>" + (name == null ? ban.uuid().toString() : name) + "</red>"));
            meta.lore(List.of(
                    TextUtil.parse("<gray>Reason: " + ban.reason() + "</gray>"),
                    TextUtil.parse("<gray>By: " + ban.bannedBy() + "</gray>"),
                    TextUtil.parse("<gray>" + expires + "</gray>")
            ));
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.LIME_DYE, "<green>No active ranked bans</green>", "All clear"));
        }
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Back</red>", "Return to staff center"));
        setSession(staff, GuiSession.of(GuiType.RANKED_BANS));
        staff.openInventory(inv);
    }

    public void openVisualReplay(Player viewer, String matchId) {
        plugin.tasks().runAsync(() -> {
            List<com.meranked.replays.ReplayService.ReplayEvent> events = services.replays().loadTimeline(matchId);
            plugin.tasks().runSync(() -> renderVisualReplay(viewer, matchId, events));
        });
    }

    private void renderVisualReplay(Player viewer, String matchId,
                                    List<com.meranked.replays.ReplayService.ReplayEvent> events) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parse("<gold>Visual Replay — " + matchId + "</gold>"));
        if (events.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, "<red>No events recorded</red>", matchId));
        } else {
            long start = events.get(0).timestamp();
            int slot = 0;
            for (var event : events) {
                if (slot >= 45) break;
                long sec = (event.timestamp() - start) / 1000;
                Material icon = services.replays().iconForType(event.eventType());
                inv.setItem(slot++, item(icon,
                        "<yellow>[" + formatReplayTime(sec) + "]</yellow> <white>" + event.eventType() + "</white>",
                        event.description()));
            }
        }
        inv.setItem(48, item(Material.BOOK, "<gray>Text Timeline</gray>", "Print to chat"));
        inv.setItem(49, item(Material.RED_CONCRETE, "<red>Close</red>", "Return to replay summary"));
        setSession(viewer, GuiSession.of(GuiType.VISUAL_REPLAY, matchId));
        viewer.openInventory(inv);
    }

    private String formatWhen(long epochMs) {
        long agoSec = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000);
        if (agoSec < 60) return agoSec + "s ago";
        if (agoSec < 3600) return (agoSec / 60) + "m ago";
        if (agoSec < 86400) return (agoSec / 3600) + "h ago";
        return (agoSec / 86400) + "d ago";
    }

    private String formatReplayTime(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    // ---- Rollback Preview ----

    public void openRollbackPreview(Player staff, String matchId) {
        plugin.tasks().runAsync(() -> {
            Map<String, Object> data = services.rollback().previewMatch(matchId);
            plugin.tasks().runSync(() -> {
                Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parse("<red>Rollback Preview — " + matchId + "</red>"));
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) data.getOrDefault("lines", List.of());
                int slot = 10;
                for (String line : lines) {
                    if (slot > 16) break;
                    inv.setItem(slot++, item(Material.PAPER, "<gray>Change</gray>", line));
                }
                inv.setItem(29, item(Material.PAPER, "<gray>Affected</gray>",
                        "Leaderboard cache", "Match history", "Peak tier", "Suspicion score"));
                inv.setItem(20, item(Material.LIME_CONCRETE, "<green>Confirm Rollback</green>", "Apply changes"));
                inv.setItem(24, item(Material.RED_CONCRETE, "<red>Cancel</red>", "Go back"));
                inv.setItem(22, item(Material.WRITABLE_BOOK, "<gold>Export Evidence</gold>", "Generate bundle"));
                setSession(staff, GuiSession.of(GuiType.ROLLBACK_PREVIEW, matchId));
                staff.openInventory(inv);
            });
        });
    }

    // ---- Punishment GUIs ----

    public void openPunishType(Player staff, UUID target, String targetName) {
        FileConfiguration cfg = configService.get("punishments.yml");
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parse(
                cfg.getString("punishments.gui.type-title", "Punish <player>").replace("<player>", targetName)));
        var section = cfg.getConfigurationSection("punishments.types");
        if (section != null) {
            for (String type : section.getKeys(false)) {
                int slot = cfg.getInt("punishments.types." + type + ".slot", 0);
                Material mat = matOf(cfg.getString("punishments.types." + type + ".material", "PAPER"));
                inv.setItem(slot, item(mat, "<gold>" + capitalize(type) + "</gold>", "Click to select"));
            }
        }
        inv.setItem(cfg.getInt("punishments.history-slot", 22), item(Material.BOOK, "<yellow>History</yellow>", "View punishment history"));
        setSession(staff, new GuiSession(GuiType.PUNISH_TYPE, target.toString(), targetName));
        staff.openInventory(inv);
    }

    public void openPunishReason(Player staff, UUID target, String targetName, String type) {
        FileConfiguration cfg = configService.get("punishments.yml");
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse(
                cfg.getString("punishments.gui.reason-title", "Select Reason").replace("<player>", targetName)));
        List<String> reasons = cfg.getStringList("punishments.types." + type + ".reasons");
        int slot = 0;
        for (String reason : reasons) {
            if (slot >= 27) break;
            inv.setItem(slot++, item(Material.PAPER, "<gold>" + reason + "</gold>", "Click to select"));
        }
        setSession(staff, new GuiSession(GuiType.PUNISH_REASON, target.toString() + "|" + type, targetName));
        staff.openInventory(inv);
    }

    public void openPunishDuration(Player staff, UUID target, String targetName, String type, String reason) {
        FileConfiguration cfg = configService.get("punishments.yml");
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse(
                cfg.getString("punishments.gui.duration-title", "Select Duration").replace("<player>", targetName)));
        List<Map<?, ?>> durations = cfg.getMapList("punishments.durations");
        int slot = 0;
        for (Map<?, ?> d : durations) {
            if (slot >= 27) break;
            String label = String.valueOf(d.get("label"));
            long seconds = ((Number) d.get("seconds")).longValue();
            inv.setItem(slot++, item(Material.CLOCK, "<gold>" + label + "</gold>", "Duration: " + label));
            // encode seconds in lore-less; handled by label lookup in listener
        }
        setSession(staff, new GuiSession(GuiType.PUNISH_DURATION, target.toString() + "|" + type + "|" + reason, targetName));
        staff.openInventory(inv);
    }

    public void openPunishConfirm(Player staff, UUID target, String targetName, String type, String reason, long durationSeconds, String durationLabel) {
        FileConfiguration cfg = configService.get("punishments.yml");
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parse(
                cfg.getString("punishments.gui.confirm-title", "Confirm Punishment")));
        inv.setItem(4, item(Material.PLAYER_HEAD, "<gold>" + targetName + "</gold>",
                "Type: " + capitalize(type), "Reason: " + reason, "Duration: " + durationLabel));
        inv.setItem(11, item(Material.LIME_CONCRETE, "<green>Confirm</green>", "Apply punishment"));
        inv.setItem(15, item(Material.RED_CONCRETE, "<red>Cancel</red>", "Discard"));
        String context = target + "|" + type + "|" + reason + "|" + durationSeconds;
        setSession(staff, new GuiSession(GuiType.PUNISH_CONFIRM, context, durationLabel));
        staff.openInventory(inv);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Material matOf(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Material.PAPER;
        }
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
