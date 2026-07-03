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
        TierDefinition best = tiers.stream()
                .filter(t -> !t.id().equals("#0"))
                .filter(t -> rating >= t.minRating() && rating <= t.maxRating())
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

    public record TierDefinition(String id, String display, int minRating, int maxRating, int demotionBuffer) {}
}
