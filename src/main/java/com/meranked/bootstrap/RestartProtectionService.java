package com.meranked.bootstrap;

import com.meranked.MERankedPlugin;
import com.meranked.queue.QueueService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles restart / reload protection and manual queue locking.
 */
public final class RestartProtectionService implements QueueService.QueueLockHolder {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final AtomicReference<String> manualLock = new AtomicReference<>(null);
    private volatile long restartAt = 0;

    public RestartProtectionService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public String lockReason() {
        if (manualLock.get() != null) return manualLock.get();
        FileConfiguration cfg = services.config().get("restart-protection.yml");
        if (!cfg.getBoolean("restart-protection.enabled", true)) return null;
        if (restartAt <= 0) return null;
        long blockBefore = cfg.getLong("restart-protection.block-new-matches-before-restart-seconds", 300) * 1000L;
        if (System.currentTimeMillis() >= restartAt - blockBefore) {
            return "server restarting soon";
        }
        return null;
    }

    /** Schedules a restart lock window; queue closes when within the block window. */
    public void scheduleRestart(long whenMillis) {
        this.restartAt = whenMillis;
        FileConfiguration cfg = services.config().get("restart-protection.yml");
        long blockBefore = cfg.getLong("restart-protection.block-new-matches-before-restart-seconds", 300) * 1000L;
        long delay = Math.max(0, (whenMillis - blockBefore - System.currentTimeMillis()));
        plugin.tasks().runSyncLater(this::onLockWindow, delay / 50);
    }

    private void onLockWindow() {
        FileConfiguration cfg = services.config().get("restart-protection.yml");
        if (cfg.getBoolean("restart-protection.cancel-queue-on-restart", true)) {
            String msg = cfg.getString("restart-protection.messages.queue-disabled", "");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (services.queue().isQueued(p.getUniqueId())) {
                    services.queue().removeFromQueue(p.getUniqueId());
                    p.sendMessage(services.messages().format(msg));
                }
            }
        }
        if (cfg.getBoolean("restart-protection.warn-active-matches", true)) {
            String warn = cfg.getString("restart-protection.messages.active-match-warning", "");
            services.matches().liveMatches().forEach(m -> {
                for (java.util.UUID uuid : java.util.List.of(m.player1(), m.player2())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendMessage(services.messages().format(warn));
                }
            });
        }
    }

    public void lockQueue(String reason) {
        manualLock.set(reason);
        FileConfiguration cfg = services.config().get("restart-protection.yml");
        String msg = cfg.getString("restart-protection.messages.queue-locked", "").replace("<reason>", reason);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (services.queue().isQueued(p.getUniqueId())) {
                services.queue().removeFromQueue(p.getUniqueId());
            }
            if (p.hasPermission("meranked.staff")) p.sendMessage(services.messages().format(msg));
        }
    }

    public void unlockQueue() {
        manualLock.set(null);
        restartAt = 0;
    }

    public boolean isLocked() {
        return lockReason() != null;
    }
}
