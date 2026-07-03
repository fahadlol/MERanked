package com.meranked.regions;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.model.RankedPlayer;
import com.meranked.rating.ProfileService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class RegionService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final MessageService messages;
    private final ProfileService profileService;

    public RegionService(MERankedPlugin plugin, ConfigService configService,
                         DatabaseService database, MessageService messages, ProfileService profileService) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.messages = messages;
        this.profileService = profileService;
    }

    public List<String> availableTags() {
        FileConfiguration config = configService.get("regions.yml");
        var section = config.getConfigurationSection("tags");
        if (section == null) return List.of("KSA", "UAE", "Other", "Hidden");
        return List.copyOf(section.getKeys(false));
    }

    public void setRegion(Player player, String tag) {
        if (!availableTags().contains(tag)) return;
        profileService.ensurePlayer(player);
        RankedPlayer rp = profileService.getPlayer(player.getUniqueId());
        String oldRegion = rp.regionHidden() ? "Hidden" : rp.region();
        RankedPlayer updated = new RankedPlayer(rp.uuid(), rp.username(), tag, "Hidden".equals(tag), rp.suspicionScore(), rp.createdAt(), System.currentTimeMillis());
        profileService.savePlayerAsync(updated);
        messages.sendPrefixed(player, "region.set", Map.of("region", tag));
        if (!oldRegion.equals(tag)) {
            plugin.services().detection().recordRegionChange(player.getUniqueId(), oldRegion, tag);
        }
    }

    public void hideRegion(Player player) {
        profileService.ensurePlayer(player);
        RankedPlayer rp = profileService.getPlayer(player.getUniqueId());
        String oldRegion = rp.regionHidden() ? "Hidden" : rp.region();
        RankedPlayer updated = new RankedPlayer(rp.uuid(), rp.username(), rp.region(), true, rp.suspicionScore(), rp.createdAt(), System.currentTimeMillis());
        profileService.savePlayerAsync(updated);
        messages.send(player, "region.hidden");
        if (!"Hidden".equals(oldRegion)) {
            plugin.services().detection().recordRegionChange(player.getUniqueId(), oldRegion, "Hidden");
        }
    }

    public String displayRegion(Player player) {
        RankedPlayer rp = profileService.getPlayer(player.getUniqueId());
        return rp.regionHidden() ? "Hidden" : rp.region();
    }
}
