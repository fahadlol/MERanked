package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Nudges hidden placement rating using in-match behavioral signals (damage, hits, totems, crystals, ping).
 */
public final class PlacementBehaviorService {

    private final ConfigService configService;

    public PlacementBehaviorService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean enabled() {
        return configService.get("placement-behavior.yml").getBoolean("placement-behavior.enabled", true);
    }

    /**
     * Computes a rating nudge from match performance and applies it to the profile's placement bias.
     * Called after each placement match ends.
     */
    public void recordPlacementMatch(RankedProfile profile, RankedMatch.MatchStats self,
                                     RankedMatch.MatchStats opponent, int ping, boolean won) {
        if (!enabled()) return;
        FileConfiguration cfg = configService.get("placement-behavior.yml");
        double weight = cfg.getDouble("placement-behavior.per-match-weight", 0.12);
        double maxNudge = cfg.getDouble("placement-behavior.max-nudge", 45);
        double minNudge = cfg.getDouble("placement-behavior.min-nudge", -45);

        double signal = computeSignal(cfg, self, opponent, ping);
        // Win amplifies positive signal, loss amplifies negative
        if (!won) signal = -Math.abs(signal) * 0.7 + signal * 0.3;

        double nudge = Math.max(minNudge, Math.min(maxNudge, signal * weight * 100));
        profile.addPlacementBehaviorBias(nudge);
    }

    /** Returns adjusted hidden rating including behavioral bias accumulated during placements. */
    public double adjustedHiddenRating(RankedProfile profile, PlacementScalingService scaling) {
        double base = scaling.hiddenRating(profile);
        if (!enabled()) return base;
        return base + profile.placementBehaviorBias();
    }

    private double computeSignal(FileConfiguration cfg, RankedMatch.MatchStats self,
                                 RankedMatch.MatchStats opponent, int ping) {
        double wDmg = cfg.getDouble("placement-behavior.signals.damage-efficiency", 0.30);
        double wHit = cfg.getDouble("placement-behavior.signals.hit-accuracy", 0.20);
        double wTotem = cfg.getDouble("placement-behavior.signals.totem-efficiency", 0.20);
        double wCrystal = cfg.getDouble("placement-behavior.signals.crystal-usage", 0.15);
        double wPing = cfg.getDouble("placement-behavior.signals.ping-normalized", 0.15);

        double totalDmg = self.damageDealt() + opponent.damageDealt();
        double dmgEff = totalDmg <= 0 ? 0.5 : self.damageDealt() / totalDmg;

        int totalHits = self.hitsLanded() + opponent.hitsLanded();
        double hitAcc = totalHits <= 0 ? 0.5 : (double) self.hitsLanded() / totalHits;

        // Lower totems popped relative to opponent = better (invert)
        int totemSum = self.totemsPopped() + opponent.totemsPopped();
        double totemEff = totemSum <= 0 ? 0.5 : 1.0 - ((double) self.totemsPopped() / totemSum);

        double crystalScore = Math.min(1.0, self.crystalsPlaced() / 8.0);
        double pingScore = Math.max(0, Math.min(1.0, 1.0 - (ping - 30) / 200.0));

        double raw = wDmg * dmgEff + wHit * hitAcc + wTotem * totemEff + wCrystal * crystalScore + wPing * pingScore;
        return (raw - 0.5) * 2.0; // map 0..1 to -1..1
    }
}
