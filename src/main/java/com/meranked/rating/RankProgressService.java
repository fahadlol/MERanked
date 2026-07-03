package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;
import com.meranked.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds rank progress bars and computes demotion-risk / next-tier information.
 */
public final class RankProgressService {

    private final ConfigService configService;
    private final TierService tierService;

    public RankProgressService(ConfigService configService, TierService tierService) {
        this.configService = configService;
        this.tierService = tierService;
    }

    /** The tier immediately above the given tier id, or null if already top. */
    public TierService.TierDefinition nextTier(String tierId) {
        List<TierService.TierDefinition> tiers = tierService.allTiers();
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(tierId) && i + 1 < tiers.size()) {
                return tiers.get(i + 1);
            }
        }
        return null;
    }

    public TierService.TierDefinition tierDef(String tierId) {
        return tierService.allTiers().stream().filter(t -> t.id().equals(tierId)).findFirst().orElse(null);
    }

    public boolean isMaxTier(String tierId) {
        List<TierService.TierDefinition> tiers = tierService.allTiers();
        return !tiers.isEmpty() && tiers.get(tiers.size() - 1).id().equals(tierId);
    }

    public long ratingToNext(RankedProfile profile) {
        TierService.TierDefinition next = nextTier(profile.tier());
        if (next == null) return 0;
        return Math.max(0, Math.round(next.minRating() - profile.rating()));
    }

    public String nextTierId(RankedProfile profile) {
        TierService.TierDefinition next = nextTier(profile.tier());
        return next == null ? profile.tier() : next.id();
    }

    /** Returns a MiniMessage-formatted progress bar string (no surrounding text). */
    public String buildBar(RankedProfile profile) {
        FileConfiguration cfg = configService.get("rank-progress.yml");
        int length = cfg.getInt("rank-progress.bar-length", 10);
        String filledChar = cfg.getString("rank-progress.filled-char", "\u2588");
        String emptyChar = cfg.getString("rank-progress.empty-char", "\u2591");
        String filledColor = cfg.getString("rank-progress.filled-color", "<green>");
        String emptyColor = cfg.getString("rank-progress.empty-color", "<dark_gray>");

        double progress = progressFraction(profile);
        int filled = (int) Math.round(progress * length);
        filled = Math.max(0, Math.min(length, filled));

        return filledColor + filledChar.repeat(filled) + emptyColor + emptyChar.repeat(length - filled);
    }

    private double progressFraction(RankedProfile profile) {
        TierService.TierDefinition current = tierDef(profile.tier());
        TierService.TierDefinition next = nextTier(profile.tier());
        if (current == null || next == null) return 1.0;
        double span = next.minRating() - current.minRating();
        if (span <= 0) return 1.0;
        return Math.max(0, Math.min(1.0, (profile.rating() - current.minRating()) / span));
    }

    /** Full formatted line, e.g. "LT3 ███████░░░ 1642 / 1700 → HT3". */
    public String formattedLine(RankedProfile profile) {
        FileConfiguration cfg = configService.get("rank-progress.yml");
        if (isMaxTier(profile.tier())) {
            return cfg.getString("rank-progress.max-tier-format", "<gold><tier></gold> <bar> <white><rating></white>")
                    .replace("<tier>", profile.tier())
                    .replace("<bar>", buildBar(profile))
                    .replace("<rating>", String.valueOf(Math.round(profile.rating())));
        }
        TierService.TierDefinition next = nextTier(profile.tier());
        return cfg.getString("rank-progress.chat-format", "")
                .replace("<tier>", profile.tier())
                .replace("<bar>", buildBar(profile))
                .replace("<rating>", String.valueOf(Math.round(profile.rating())))
                .replace("<next_rating>", String.valueOf(next.minRating()))
                .replace("<next_tier>", next.id());
    }

    public void sendChatProgress(Player player, RankedProfile profile) {
        FileConfiguration cfg = configService.get("rank-progress.yml");
        if (!cfg.getBoolean("rank-progress.enabled", true) || !cfg.getBoolean("rank-progress.show-in-chat", true)) return;
        player.sendMessage(TextUtil.parse(formattedLine(profile)));
    }

    public void sendActionBarProgress(Player player, RankedProfile profile) {
        FileConfiguration cfg = configService.get("rank-progress.yml");
        if (!cfg.getBoolean("rank-progress.enabled", true) || !cfg.getBoolean("rank-progress.show-in-actionbar", true)) return;
        player.sendActionBar(TextUtil.parse(formattedLine(profile)));
    }

    // ---- Demotion risk ----

    /** Rating at/below which the player would demote out of their current tier. */
    public int demotionRating(RankedProfile profile) {
        return tierService.demotionRating(profile.tier(), profile.rating());
    }

    public int safeRating(RankedProfile profile) {
        return demotionRating(profile) + 1;
    }

    public boolean atDemotionRisk(RankedProfile profile) {
        FileConfiguration cfg = configService.get("demotion.yml");
        if (!cfg.getBoolean("demotion.enabled", true)) return false;
        if (profile.tier().equals("#0")) return false;
        int threshold = cfg.getInt("demotion.warn-threshold", 25);
        int safe = safeRating(profile);
        return profile.rating() >= safe && profile.rating() <= safe + threshold;
    }

    public String nextLowerTierId(String tierId) {
        List<TierService.TierDefinition> tiers = tierService.allTiers();
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(tierId) && i - 1 >= 0) {
                return tiers.get(i - 1).id();
            }
        }
        return tierId;
    }
}
