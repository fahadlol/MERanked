package com.meranked.core.listeners;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class DiscordBridgeListener implements Listener {

    private final ServiceRegistry services;
    private final AtomicLong lastTpsWarning = new AtomicLong(0);
    private final AtomicLong lastMemoryWarning = new AtomicLong(0);

    public DiscordBridgeListener(ServiceRegistry services) {
        this.services = services;
        startPerformanceMonitor();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("meranked.staff")) {
            services.staffDuty().handleJoin(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("meranked.staff")) {
            services.staffDuty().handleQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var cfg = services.config().get("config.yml");
        if (!cfg.getBoolean("command-logging.enabled", true)) return;
        Player player = event.getPlayer();
        if (cfg.getBoolean("command-logging.staff-commands-only", true)
                && !player.hasPermission("meranked.staff")) return;

        String raw = event.getMessage().substring(1);
        String cmd = raw.split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> ignored = cfg.getStringList("command-logging.ignored-commands");
        if (ignored.stream().anyMatch(i -> i.equalsIgnoreCase(cmd))) return;

        List<String> dangerous = cfg.getStringList("command-logging.dangerous-commands");
        boolean isDangerous = dangerous.stream().anyMatch(d -> d.equalsIgnoreCase(cmd));
        LogSeverity severity = isDangerous ? LogSeverity.HIGH : LogSeverity.LOW;
        String type = isDangerous ? "STAFF_DANGEROUS_COMMAND" : "STAFF_COMMAND";

        services.logger().logStaff(LogCategory.STAFF, type, severity,
                player.getName() + " used /" + raw,
                player.getName(), player.getUniqueId(),
                Map.of("command", cmd));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.getPlayer().hasPermission("meranked.staff")) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;
        services.logger().logStaff(LogCategory.STAFF, "STAFF_TELEPORT", LogSeverity.LOW,
                event.getPlayer().getName() + " teleported",
                event.getPlayer().getName(), event.getPlayer().getUniqueId(),
                Map.of("to", event.getTo().getWorld().getName()));
    }

    private void startPerformanceMonitor() {
        Bukkit.getScheduler().runTaskTimer(services.plugin(), () -> {
            var cfg = services.config().get("config.yml");
            if (!cfg.getBoolean("performance-logs.enabled", true)) return;
            long cooldownMs = cfg.getInt("performance-logs.cooldown-seconds", 300) * 1000L;
            long now = System.currentTimeMillis();

            double tps = Bukkit.getTPS()[0];
            double tpsWarn = cfg.getDouble("performance-logs.tps-warning", 17.0);
            if (tps < tpsWarn && now - lastTpsWarning.get() > cooldownMs) {
                lastTpsWarning.set(now);
                services.logger().log(LogCategory.SYSTEM, "TPS_WARNING", LogSeverity.HIGH,
                        "Server TPS dropped to " + String.format(Locale.ROOT, "%.1f", tps));
            }

            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            int percent = (int) (used * 100 / rt.maxMemory());
            int memWarn = cfg.getInt("performance-logs.memory-warning-percent", 85);
            if (percent >= memWarn && now - lastMemoryWarning.get() > cooldownMs) {
                lastMemoryWarning.set(now);
                services.logger().log(LogCategory.SYSTEM, "MEMORY_WARNING", LogSeverity.HIGH,
                        "Memory usage at " + percent + "%");
            }
        }, 200L, 200L);
    }
}
