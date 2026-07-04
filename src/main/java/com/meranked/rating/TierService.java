package com.meranked.rating;

import com.meranked.config.ConfigService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TierService {

    private final ConfigService configService;
    private final List<TierDefinition> tiers = new ArrayList<>();

    public TierService(ConfigService configService) {
        this.configService = configService;
        reload(configService);
    }

    public void reload(ConfigService configService) {
        tiers.clear();
        FileConfiguration config = configService.get("tiers.yml");
        ConfigurationSection section = config.getConfigurationSection("tiers");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection tierSec = section.getConfigurationSection(key);
            if (tierSec == null) continue;
            tiers.add(new TierDefinition(
                    tierSec.getString("id", key),
                    tierSec.getString("display", key),
                    tierSec.getInt("min-rating", 0),
                    tierSec.getInt("max-rating", 99999),
                    tierSec.getInt("demotion-buffer", 40)
            ));
        }
        tiers.sort(Comparator.comparingInt(TierDefinition::minRating));
    }

    public String getTierForRating(double rating, boolean unranked) {
        if (unranked) return "#0";
        final double effective = applyNonlinearBucketing(rating);
        TierDefinition best = tiers.stream()
                .filter(t -> !t.id().equals("#0"))
                .filter(t -> effective >= t.minRating() && effective <= t.maxRating())
                .findFirst()
                .orElse(tiers.get(tiers.size() - 1));
        return best.id();
    }

    public String getConfidenceLabel(double rd) {
        FileConfiguration config = configService.get("tiers.yml");
        ConfigurationSection conf = config.getConfigurationSection("confidence");
        if (conf == null) return "Calibrating";
        for (String key : conf.getKeys(false)) {
            ConfigurationSection c = conf.getConfigurationSection(key);
            if (c == null) continue;
            int min = c.getInt("min-rd");
            int max = c.getInt("max-rd");
            if (rd >= min && rd <= max) return c.getString("display", key);
        }
        return "Calibrating";
    }

    public int demotionRating(String tierId, double currentRating) {
        TierDefinition tier = tiers.stream().filter(t -> t.id().equals(tierId)).findFirst().orElse(null);
        if (tier == null) return (int) currentRating;
        return tier.minRating() - tier.demotionBuffer();
    }

    public boolean wouldDemote(String currentTier, double newRating) {
        TierDefinition tier = tiers.stream().filter(t -> t.id().equals(currentTier)).findFirst().orElse(null);
        if (tier == null || currentTier.equals("#0")) return false;
        return newRating < demotionRating(currentTier, newRating);
    }

    public String capPlacementTier(String tier, String maxTier) {
        int tierIndex = indexOf(tier);
        int maxIndex = indexOf(maxTier);
        if (tierIndex <= maxIndex) return tier;
        return maxTier;
    }

    private int indexOf(String tierId) {
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(tierId)) return i;
        }
        return 0;
    }

    public List<TierDefinition> allTiers() {
        return List.copyOf(tiers);
    }

    /**
     * Snaps rating toward the nearest non-linear bucket center for finer mid-tier granularity.
     * Buckets are narrower near the population median (inverted-normal width curve).
     */
    private double applyNonlinearBucketing(double rating) {
        FileConfiguration config = configService.get("tiers.yml");
        if (!config.getBoolean("nonlinear-bucketing.enabled", false)) return rating;

        double center = config.getDouble("nonlinear-bucketing.center-rating", 1500);
        double minWidth = config.getDouble("nonlinear-bucketing.min-bucket-width", 80);
        double maxWidth = config.getDouble("nonlinear-bucketing.max-bucket-width", 200);
        int count = config.getInt("nonlinear-bucketing.bucket-count", 12);

        double[] edges = buildBucketEdges(center, minWidth, maxWidth, count);
        for (int i = 0; i < edges.length - 1; i++) {
            if (rating >= edges[i] && rating < edges[i + 1]) {
                return (edges[i] + edges[i + 1]) / 2.0;
            }
        }
        return rating;
    }

    private double[] buildBucketEdges(double center, double minWidth, double maxWidth, int count) {
        double low = 800;
        double high = 2700;
        double[] edges = new double[count + 1];
        double span = high - low;
        double pos = 0;
        for (int i = 0; i <= count; i++) {
            edges[i] = low + pos;
            if (i == count) break;
            double t = (pos / span) * 2 - 1; // -1..1 from low to high
            double width = minWidth + (maxWidth - minWidth) * t * t;
            pos += width;
        }
        edges[count] = high;
        return edges;
    }

    public record TierDefinition(String id, String display, int minRating, int maxRating, int demotionBuffer) {}
}
