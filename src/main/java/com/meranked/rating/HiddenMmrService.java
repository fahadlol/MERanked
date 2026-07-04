package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Maintains a hidden MMR separate from visible tier rating for fairer matchmaking and catch-up progression.
 */
public final class HiddenMmrService {

    private final ConfigService configService;
    private final TierService tierService;

    public HiddenMmrService(ConfigService configService, TierService tierService) {
        this.configService = configService;
        this.tierService = tierService;
    }

    public boolean enabled() {
        return configService.get("hidden-mmr.yml").getBoolean("hidden-mmr.enabled", true);
    }

    /** Rating used for matchmaking (hidden MMR when enabled and ranked). */
    public double matchmakingRating(RankedProfile profile, boolean inPlacements, double placementRating) {
        if (inPlacements) return placementRating;
        if (!enabled() || !configService.get("hidden-mmr.yml").getBoolean("hidden-mmr.use-for-matchmaking", true)) {
            return profile.rating();
        }
        return profile.hiddenMmr();
    }

    /** Sync hidden MMR after a rating change. */
    public void afterRatingChange(RankedProfile profile, double oldRating, double newRating, boolean won) {
        if (!enabled()) return;
        FileConfiguration cfg = configService.get("hidden-mmr.yml");
        double hidden = profile.hiddenMmr();
        hidden += (newRating - oldRating);
        if (won) {
            hidden += catchUpBonus(profile);
        }
        // Converge hidden toward visible rating over time
        double rate = cfg.getDouble("hidden-mmr.convergence-rate", 0.15);
        hidden = hidden * (1 - rate) + profile.rating() * rate;
        profile.setHiddenMmr(hidden);
    }

    /** Extra rating applied when hidden MMR is well above visible tier (soft catch-up). */
    public double catchUpBonus(RankedProfile profile) {
        if (!enabled()) return 0;
        FileConfiguration cfg = configService.get("hidden-mmr.yml");
        double threshold = cfg.getDouble("hidden-mmr.catch-up-threshold", 80);
        double multiplier = cfg.getDouble("hidden-mmr.catch-up-multiplier", 1.35);
        double maxBonus = cfg.getDouble("hidden-mmr.max-catch-up-bonus", 28);

        int tierRating = tierMidpoint(profile.tier());
        double gap = profile.hiddenMmr() - tierRating;
        if (gap < threshold) return 0;
        return Math.min(maxBonus, (gap - threshold) * 0.08 * multiplier);
    }

    /** Gap between hidden MMR and visible tier midpoint (for transparency). */
    public double mmrGap(RankedProfile profile) {
        return profile.hiddenMmr() - tierMidpoint(profile.tier());
    }

    private int tierMidpoint(String tierId) {
        return tierService.allTiers().stream()
                .filter(t -> t.id().equals(tierId))
                .findFirst()
                .map(t -> (t.minRating() + t.maxRating()) / 2)
                .orElse(1500);
    }
}
