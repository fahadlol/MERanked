package com.meranked.core.staff;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.core.logs.LogCategory;
import com.meranked.core.logs.LogSeverity;
import com.meranked.core.logs.MerankedLoggerService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffDutyService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final MerankedLoggerService logger;
    private final Set<UUID> onDuty = ConcurrentHashMap.newKeySet();

    public StaffDutyService(MERankedPlugin plugin, ConfigService configService, MerankedLoggerService logger) {
        this.plugin = plugin;
        this.configService = configService;
        this.logger = logger;
    }

    public boolean enabled() {
        return cfg().getBoolean("staff-duty.enabled", true);
    }

    public boolean toggleDuty(Player player) {
        if (!enabled()) return false;
        if (onDuty.contains(player.getUniqueId())) {
            setOffDuty(player);
            return false;
        }
        setOnDuty(player);
        return true;
    }

    public void setOnDuty(Player player) {
        onDuty.add(player.getUniqueId());
        logger.logStaff(LogCategory.STAFF, "STAFF_DUTY_ON", LogSeverity.INFO,
                player.getName() + " is now on duty",
                player.getName(), player.getUniqueId(), null);
    }

    public void setOffDuty(Player player) {
        onDuty.remove(player.getUniqueId());
        logger.logStaff(LogCategory.STAFF, "STAFF_DUTY_OFF", LogSeverity.INFO,
                player.getName() + " is now off duty",
                player.getName(), player.getUniqueId(), null);
    }

    public boolean isOnDuty(UUID uuid) {
        return onDuty.contains(uuid);
    }

    public int onDutyCount() {
        return onDuty.size();
    }

    public Set<UUID> onDutyStaff() {
        return Set.copyOf(onDuty);
    }

    public void handleJoin(Player player) {
        if (!enabled()) return;
        if (!player.hasPermission("meranked.staff")) return;
        logger.logStaff(LogCategory.STAFF, "STAFF_JOIN", LogSeverity.INFO,
                "Staff joined: " + player.getName(),
                player.getName(), player.getUniqueId(), null);
        if (cfg().getBoolean("staff-duty.auto-duty-on-minecraft-join", false)) {
            setOnDuty(player);
        }
    }

    public void handleQuit(Player player) {
        if (!player.hasPermission("meranked.staff")) return;
        onDuty.remove(player.getUniqueId());
        logger.logStaff(LogCategory.STAFF, "STAFF_LEAVE", LogSeverity.INFO,
                "Staff left: " + player.getName(),
                player.getName(), player.getUniqueId(), null);
    }

    public boolean canAcceptTicket(UUID staffUuid) {
        if (!enabled()) return false;
        if (!isOnDuty(staffUuid)) return false;
        int max = cfg().getInt("staff-duty.max-active-tickets-per-staff", 3);
        return max > 0;
    }

    private FileConfiguration cfg() {
        return configService.get("config.yml");
    }
}
