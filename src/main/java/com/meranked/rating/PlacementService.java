package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;

public final class PlacementService {

    private final ConfigService configService;
    private final TierService tierService;
    private final ProfileService profileService;
    private PlacementScalingService scalingService;

    public PlacementService(ConfigService configService, TierService tierService, ProfileService profileService) {
        this.configService = configService;
        this.tierService = tierService;
        this.profileService = profileService;
    }

    public void bindScaling(PlacementScalingService scalingService) {
        this.scalingService = scalingService;
    }

    public void recordPlacementMatch(RankedProfile profile, boolean won, String opponentName,
                                     String opponentTier, double opponentRating) {
        profile.setPlacementPlayed(profile.placementPlayed() + 1);
        profile.addPlacementOpponentRating(opponentRating);
        if (won) {
            profile.setPlacementWins(profile.placementWins() + 1);
            if (profile.bestWinOpponent() == null || tierIndex(opponentTier) > tierIndex(profile.bestWinTier())) {
                profile.setBestWinOpponent(opponentName);
                profile.setBestWinTier(opponentTier);
            }
        } else {
            profile.setPlacementLosses(profile.placementLosses() + 1);
            if (profile.worstPlacementLossRating() == 0 || opponentRating < profile.worstPlacementLossRating()) {
                profile.setWorstPlacementLossRating(opponentRating);
            }
        }
    }

    public boolean completePlacementsIfReady(RankedProfile profile) {
        int required = profileService.placementRequired(profile.gamemode());
        if (profile.placementPlayed() < required) return false;

        profile.setRanked(true);
        if (scalingService != null) {
            double refined = scalingService.refineFinalRating(profile);
            refined += profile.placementBehaviorBias();
            profile.setRating(refined);
        }
        profile.setHiddenMmr(profile.rating());
        String tier = tierService.getTierForRating(profile.rating(), false);
        FileConfiguration gamemodes = configService.get("gamemodes.yml");
        String maxTier = gamemodes.getString("gamemodes." + profile.gamemode() + ".max-placement-tier", "LT3");
        // account trust may further lower the cap (alt confidence lock)
        if (profile.placementCapOverride() != null && !profile.placementCapOverride().isEmpty()) {
            maxTier = lowerOf(maxTier, profile.placementCapOverride());
        }
        tier = tierService.capPlacementTier(tier, maxTier);
        profile.setTier(tier);
        profileService.updatePeak(profile);
        return true;
    }

    /** Returns whichever tier id is lower-ranked (further from #1). */
    private String lowerOf(String a, String b) {
        return tierIndex(a) <= tierIndex(b) ? a : b;
    }

    public String averageOpponentTier(RankedProfile profile) {
        return tierService.getTierForRating(profile.averagePlacementOpponentRating(), false);
    }

    public String displayTier(RankedProfile profile) {
        if (profile.inPlacements(profileService.placementRequired(profile.gamemode()))) {
            return "#0";
        }
        return profile.tier();
    }

    private int tierIndex(String tier) {
        if (tier == null) return 0;
        return tierService.allTiers().stream().map(TierService.TierDefinition::id).toList().indexOf(tier);
    }
}
