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
            case SETTINGS -> handleSettings(player, event.getRawSlot());
            case STAFF_ALERTS -> handleStaffAlerts(player, event.getRawSlot());
            case STAFF_ALERT_DETAIL -> handleAlertDetail(player, session, event.getRawSlot());
            case ROLLBACK_CONFIRM -> handleRollback(player, session, event.getRawSlot());
            case DEFAULT_KIT_CONFIRM -> handleDefaultKit(player, event.getRawSlot());
            case PLACEMENT_RECAP -> { if (event.getRawSlot() == 22) player.closeInventory(); }
            case KIT_AUDIT -> handleKitAudit(player, session, event.getRawSlot());
            case TRIM_EDITOR -> handleTrimEditor(player, session, event.getRawSlot());
            case REPLAY -> handleReplay(player, session, event.getRawSlot());
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
}
