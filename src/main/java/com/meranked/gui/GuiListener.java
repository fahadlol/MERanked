package com.meranked.gui;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.StaffAlert;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

public final class GuiListener implements Listener {

    private final ServiceRegistry services;

    public GuiListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = services.gui().session(player);
        if (session == null) return;

        // The ender chest editor is a real editable inventory — allow item movement.
        if (session.type() == GuiType.ENDER_CHEST_EDITOR) return;

        event.setCancelled(true);

        switch (session.type()) {
            case RANKED_MENU -> handleRankedMenu(player, event.getRawSlot());
            case KIT_EDITOR_MENU -> handleKitEditorMenu(player, event.getRawSlot());
            case SETTINGS -> handleSettings(player, event.getRawSlot());
            case STAFF_ALERTS -> handleStaffAlerts(player, event.getRawSlot());
            case STAFF_ALERT_DETAIL -> handleAlertDetail(player, session, event.getRawSlot());
            case ROLLBACK_CONFIRM -> handleRollback(player, session, event.getRawSlot());
            case DEFAULT_KIT_CONFIRM -> handleDefaultKit(player, event.getRawSlot());
            case PLACEMENT_RECAP -> { if (event.getRawSlot() == 22) player.closeInventory(); }
            case KIT_AUDIT -> handleKitAudit(player, session, event.getRawSlot());
            case TRIM_EDITOR -> handleTrimEditor(player, session, event.getRawSlot());
            case REPLAY -> handleReplay(player, session, event.getRawSlot());
            case STAFF_CENTER -> handleStaffCenter(player, event.getRawSlot());
            case STAFF_PANEL -> { if (event.getRawSlot() == 49) player.closeInventory(); }
            case ROLLBACK_PREVIEW -> handleRollbackPreview(player, session, event.getRawSlot());
            case PUNISH_TYPE -> handlePunishType(player, session, event.getRawSlot());
            case PUNISH_REASON -> handlePunishReason(player, session, event.getRawSlot());
            case PUNISH_DURATION -> handlePunishDuration(player, session, event.getRawSlot());
            case PUNISH_CONFIRM -> handlePunishConfirm(player, session, event.getRawSlot());
            case IDENTITY_CARD -> {}
            case FAIRNESS_DASHBOARD -> { if (event.getRawSlot() >= 40) player.closeInventory(); }
            default -> {}
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            GuiSession session = services.gui().session(player);
            if (session != null && session.type() == GuiType.ENDER_CHEST_EDITOR) {
                services.kitEditor().setEnderChest(player.getUniqueId(), event.getInventory().getContents());
                player.sendMessage("§aEnder chest kit updated (save the kit to persist).");
            }
            services.gui().clearSession(player);
        }
    }

    private void handleRankedMenu(Player player, int slot) {
        if (slot == 40) { services.gui().openSettings(player); return; }
        if (slot == 41) { services.gui().openProfile(player, null); return; }
        if (slot == 42) { services.gui().openLeaderboard(player, null); return; }
        if (slot >= 10 && slot <= 34) {
            int index = slot - 10;
            var modes = services.profiles().enabledGamemodes();
            if (index < modes.size()) services.queue().joinQueue(player, modes.get(index));
        }
    }

    private void handleKitEditorMenu(Player player, int slot) {
        if (slot >= 10 && slot <= 34) {
            int index = slot - 10;
            var modes = services.profiles().enabledGamemodes();
            if (index < modes.size()) {
                player.closeInventory();
                services.kitEditor().enterEditor(player, modes.get(index));
            }
        }
    }

    private void handleSettings(Player player, int slot) {
        var cfg = services.config().get("settings.yml");
        if (slot == cfg.getInt("options.messages.slot", 10)) services.settings().toggle(player.getUniqueId(), "messages");
        if (slot == cfg.getInt("options.spectate-requests.slot", 12)) services.settings().toggle(player.getUniqueId(), "spectate-requests");
        if (slot == cfg.getInt("options.queue-notifications.slot", 14)) services.settings().toggle(player.getUniqueId(), "queue-notifications");
        if (slot == cfg.getInt("options.region-display.slot", 16)) {
            services.settings().toggle(player.getUniqueId(), "region-display");
            var s = services.settings().get(player.getUniqueId());
            var rp = services.profiles().getPlayer(player.getUniqueId());
            services.profiles().savePlayerAsync(new com.meranked.model.RankedPlayer(
                    rp.uuid(), rp.username(), rp.region(), s.regionHidden(), rp.suspicionScore(), rp.createdAt(), System.currentTimeMillis()));
        }
        services.gui().openSettings(player);
    }

    private void handleStaffAlerts(Player player, int slot) {
        var alerts = services.alerts().recentAlerts();
        if (slot >= 0 && slot < alerts.size()) {
            services.gui().openAlertDetail(player, alerts.get(slot));
        }
    }

    private void handleAlertDetail(Player player, GuiSession session, int slot) {
        String alertId = session.context();
        String matchId = session.subContext();
        switch (slot) {
            case 13 -> { if (!matchId.isEmpty()) services.gui().openRollbackConfirm(player, matchId); }
            case 14 -> { if (!matchId.isEmpty()) services.gui().openReplay(player, matchId); }
            case 15 -> {
                services.alerts().resolveAlert(alertId);
                player.sendMessage("§aAlert resolved.");
                player.closeInventory();
            }
            case 16 -> {
                var alert = services.alerts().recentAlerts().stream()
                        .filter(a -> a.alertId().equals(alertId)).findFirst().orElse(null);
                if (alert != null && !alert.players().isEmpty()) {
                    String first = alert.players().split(",")[0].trim();
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(first);
                    services.watchlist().add(op.getUniqueId(), first, player.getName(), "From alert " + alert.type());
                    player.sendMessage("§aAdded " + first + " to watchlist.");
                }
                player.closeInventory();
            }
            default -> {}
        }
    }

    private void handleRollback(Player player, GuiSession session, int slot) {
        if (slot == 11) {
            services.rollback().rollbackMatch(player, session.context(), "Alert rollback");
            player.closeInventory();
        }
        if (slot == 15) player.closeInventory();
    }

    private void handleDefaultKit(Player player, int slot) {
        if (slot == 45) services.defaultKits().confirm(player);
        if (slot == 53) services.defaultKits().cancel(player);
        player.closeInventory();
    }

    private void handleKitAudit(Player staff, GuiSession session, int slot) {
        UUID target = UUID.fromString(session.context());
        String gamemode = session.subContext();
        if (slot == 30) {
            services.kits().resetKit(target, gamemode);
            staff.sendMessage("§aKit reset.");
        }
    }

    private void handleTrimEditor(Player player, GuiSession session, int slot) {
        var cfg = services.config().get("kit-editor.yml");
        java.util.List<String> materials = services.gui().trimMaterials(cfg);
        java.util.List<String> patterns = services.gui().trimPatterns(cfg);
        String material = session.context();
        String pattern = session.subContext();
        switch (slot) {
            case 11 -> {
                int idx = (materials.indexOf(material) + 1) % materials.size();
                services.gui().renderTrimEditor(player, materials.get(idx), pattern);
            }
            case 13 -> {
                int idx = (patterns.indexOf(pattern) + 1) % patterns.size();
                services.gui().renderTrimEditor(player, material, patterns.get(idx));
            }
            case 15 -> {
                applyTrim(player, material, pattern);
                player.sendMessage("§aTrim applied to armor.");
                player.closeInventory();
            }
            case 16 -> {
                applyTrim(player, null, null);
                player.sendMessage("§eTrims removed.");
                player.closeInventory();
            }
            default -> {}
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void applyTrim(Player player, String materialKey, String patternKey) {
        org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents();
        for (org.bukkit.inventory.ItemStack piece : armor) {
            if (piece == null) continue;
            if (!(piece.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta meta)) continue;
            if (materialKey == null || patternKey == null) {
                meta.setTrim(null);
            } else {
                try {
                    org.bukkit.inventory.meta.trim.TrimMaterial mat = resolveTrimMaterial(materialKey);
                    org.bukkit.inventory.meta.trim.TrimPattern pat = resolveTrimPattern(patternKey);
                    if (mat != null && pat != null) {
                        meta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(mat, pat));
                    }
                } catch (Exception ignored) {}
            }
            piece.setItemMeta(meta);
        }
        player.getInventory().setArmorContents(armor);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private org.bukkit.inventory.meta.trim.TrimMaterial resolveTrimMaterial(String key) {
        return org.bukkit.Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(key.toLowerCase()));
    }

    @SuppressWarnings({"deprecation", "removal"})
    private org.bukkit.inventory.meta.trim.TrimPattern resolveTrimPattern(String key) {
        return org.bukkit.Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(key.toLowerCase()));
    }

    private void handleReplay(Player player, GuiSession session, int slot) {
        if (slot == 16) {
            services.replays().printTimeline(player, session.context());
            player.closeInventory();
        }
    }

    private void handleStaffCenter(Player player, int slot) {
        var cfg = services.config().get("staff-center.yml");
        if (slot == cfg.getInt("staff-center.items.staff-alerts", 12)) { services.gui().openStaffAlerts(player); }
        else if (slot == cfg.getInt("staff-center.items.watchlist", 14)) { services.gui().openWatchlist(player); }
        else if (slot == cfg.getInt("staff-center.items.close", 49)) { player.closeInventory(); }
        else if (slot == cfg.getInt("staff-center.items.suspicious-players", 16)) {
            player.closeInventory();
            player.performCommand("ranked suspicious");
        } else if (slot == cfg.getInt("staff-center.items.live-matches", 10)) {
            player.closeInventory();
            player.performCommand("matches");
        }
    }

    private void handleRollbackPreview(Player player, GuiSession session, int slot) {
        String matchId = session.context();
        switch (slot) {
            case 20 -> {
                services.rollback().rollbackMatch(player, matchId, "Rollback preview confirm");
                player.sendMessage("§aRollback applied for " + matchId + ".");
                player.closeInventory();
            }
            case 22 -> {
                services.evidence().generateForMatch(player.getUniqueId(), matchId);
                player.sendMessage("§aEvidence bundle generated.");
            }
            case 24 -> player.closeInventory();
            default -> {}
        }
    }

    private void handlePunishType(Player staff, GuiSession session, int slot) {
        UUID target = UUID.fromString(session.context());
        String name = session.subContext();
        var cfg = services.config().get("punishments.yml");
        var section = cfg.getConfigurationSection("punishments.types");
        if (slot == cfg.getInt("punishments.history-slot", 22)) {
            staff.closeInventory();
            staff.performCommand("history " + name);
            return;
        }
        if (section == null) return;
        for (String type : section.getKeys(false)) {
            if (cfg.getInt("punishments.types." + type + ".slot", -1) == slot) {
                String perm = cfg.getString("punishments.types." + type + ".permission", "meranked.punish");
                if (!staff.hasPermission(perm)) { staff.sendMessage("§cNo permission for that punishment."); return; }
                services.gui().openPunishReason(staff, target, name, type);
                return;
            }
        }
    }

    private void handlePunishReason(Player staff, GuiSession session, int slot) {
        String[] ctx = session.context().split("\\|", 2);
        UUID target = UUID.fromString(ctx[0]);
        String type = ctx[1];
        String name = session.subContext();
        var cfg = services.config().get("punishments.yml");
        java.util.List<String> reasons = cfg.getStringList("punishments.types." + type + ".reasons");
        if (slot >= 0 && slot < reasons.size()) {
            services.gui().openPunishDuration(staff, target, name, type, reasons.get(slot));
        }
    }

    private void handlePunishDuration(Player staff, GuiSession session, int slot) {
        String[] ctx = session.context().split("\\|", 3);
        UUID target = UUID.fromString(ctx[0]);
        String type = ctx[1];
        String reason = ctx[2];
        String name = session.subContext();
        var cfg = services.config().get("punishments.yml");
        java.util.List<java.util.Map<?, ?>> durations = cfg.getMapList("punishments.durations");
        if (slot >= 0 && slot < durations.size()) {
            var d = durations.get(slot);
            String label = String.valueOf(d.get("label"));
            long seconds = ((Number) d.get("seconds")).longValue();
            if (seconds < 0) {
                // Custom: default to permanent for simplicity (chat prompt could be added).
                seconds = 0;
                label = "Permanent";
            }
            services.gui().openPunishConfirm(staff, target, name, type, reason, seconds, label);
        }
    }

    private void handlePunishConfirm(Player staff, GuiSession session, int slot) {
        if (slot == 15) { staff.closeInventory(); return; }
        if (slot != 11) return;
        String[] ctx = session.context().split("\\|", 4);
        UUID target = UUID.fromString(ctx[0]);
        String type = ctx[1];
        String reason = ctx[2];
        long seconds = Long.parseLong(ctx[3]);
        com.meranked.staff.PunishmentService.Type ptype;
        try {
            ptype = com.meranked.staff.PunishmentService.Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            ptype = com.meranked.staff.PunishmentService.Type.BAN;
        }
        services.punishments().punish(target, staff.getUniqueId(), ptype, reason, seconds * 1000L, null);
        var cfg = services.config().get("punishments.yml");
        staff.sendMessage(services.messages().format(cfg.getString("punishments.messages.confirm", "")
                .replace("<player>", session.subContext())));
        staff.closeInventory();
    }
}
