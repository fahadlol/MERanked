package com.meranked.rating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class DecayService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final ProfileService profileService;
    private final RatingService ratingService;

    public DecayService(MERankedPlugin plugin, ConfigService configService,
                        ProfileService profileService, RatingService ratingService) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
        this.ratingService = ratingService;
    }

    public void start() {
        FileConfiguration config = configService.get("config.yml");
        if (!config.getBoolean("decay.enabled", true)) return;
        plugin.tasks().runAsyncTimer(this::tick, 20L * 60 * 60, 20L * 60 * 60 * 6);
    }

    private void tick() {
        FileConfiguration config = configService.get("config.yml");
        long inactiveMs = config.getLong("decay.inactive-days", 14) * 86400000L;
        double rdIncrease = config.getDouble("decay.rd-increase-per-day", 2.0);
        double maxIncrease = config.getDouble("decay.max-rd-increase", 50.0);
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyReturnDecay(player.getUniqueId(), inactiveMs, rdIncrease, maxIncrease, now);
        }
    }

    public void applyReturnDecay(UUID uuid, long inactiveMs, double rdIncrease, double maxIncrease, long now) {
        for (String gamemode : profileService.enabledGamemodes()) {
            RankedProfile profile = profileService.getProfile(uuid, gamemode);
            if (profile.lastPlayed() <= 0 || profile.inPlacements(profileService.placementRequired(gamemode))) continue;
            long inactive = now - profile.lastPlayed();
            if (inactive < inactiveMs) continue;
            long days = inactive / 86400000L;
            double increase = Math.min(maxIncrease, days * rdIncrease);
            if (increase > 0 && profile.ratingDeviation() < 350) {
                ratingService.applyDecay(profile, increase);
                profile.setDecayActive(true);
                profileService.queueSave(profile);
            }
        }
    }

    public void forceDecay(Player staff, UUID target, String gamemode) {
        RankedProfile profile = profileService.getProfile(target, gamemode);
        ratingService.applyDecay(profile, configService.get("config.yml").getDouble("decay.max-rd-increase", 50));
        profileService.queueSave(profile);
    }
}
