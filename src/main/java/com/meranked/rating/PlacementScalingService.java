package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Adjusts placement matchmaking difficulty based on the player's running W-L record,
 * and refines the final placed rating using the difficulty they faced.
 */
public final class PlacementScalingService {

    private final ConfigService configService;

    public PlacementScalingService(ConfigService configService) {
        this.configService = configService;
    }

    /** Hidden matchmaking rating used while a player is completing placements. */
    public double hiddenRating(RankedProfile profile) {
        FileConfiguration cfg = configService.get("placement-scaling.yml");
        if (!cfg.getBoolean("enabled", true)) return profile.rating();

        double base = cfg.getDouble("difficulty.base-hidden-rating", 1500);
        double perWin = cfg.getDouble("difficulty.per-net-win", 65);
        double perLoss = cfg.getDouble("difficulty.per-net-loss", 65);
        double min = cfg.getDouble("difficulty.min-hidden-rating", 1100);
        double max = cfg.getDouble("difficulty.max-hidden-rating", 1750);

        int net = profile.placementNetWins();
        double target = base + (net >= 0 ? net * perWin : net * perLoss);

        int testThreshold = cfg.getInt("difficulty.test-threshold-net-wins", 4);
        if (net >= testThreshold) {
            target = Math.max(target, cfg.getDouble("difficulty.test-target-rating", 1700));
        }
        return Math.max(min, Math.min(max, target));
    }

    /**
     * Nudges the raw placed rating toward the average opponent difficulty faced,
     * so lucky/easy placements don't over-reward and hard placements don't under-reward.
     */
    public double refineFinalRating(RankedProfile profile) {
        FileConfiguration cfg = configService.get("placement-scaling.yml");
        if (!cfg.getBoolean("enabled", true)) return profile.rating();
        double weight = cfg.getDouble("final-accuracy-weight", 0.35);
        double avgOpp = profile.averagePlacementOpponentRating();
        double raw = profile.rating();
        return raw + (avgOpp - raw) * weight;
    }
}
