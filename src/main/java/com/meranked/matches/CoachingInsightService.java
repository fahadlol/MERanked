package com.meranked.matches;

import com.meranked.config.ConfigService;
import com.meranked.model.QueueEntry;
import com.meranked.model.RankedProfile;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Generates post-match coaching insight lines from combat statistics.
 */
public final class CoachingInsightService {

    private final ConfigService configService;

    public CoachingInsightService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean enabled() {
        return configService.get("coaching-insights.yml").getBoolean("coaching-insights.enabled", true);
    }

    public java.util.List<String> insights(com.meranked.model.RankedMatch match, java.util.UUID uuid, int ping) {
        if (!enabled()) return java.util.List.of();
        FileConfiguration cfg = configService.get("coaching-insights.yml");
        int max = cfg.getInt("coaching-insights.max-insights", 3);
        var self = match.stats(uuid);
        var opp = match.stats(match.opponent(uuid));

        java.util.List<String> lines = new java.util.ArrayList<>();
        double totalDmg = self.damageDealt() + opp.damageDealt();
        if (totalDmg > 0) {
            int pct = (int) Math.round(self.damageDealt() / totalDmg * 100);
            if (pct < 45) {
                lines.add(resolve(cfg, "coaching-insights.templates.damage-low", pct, self, ping));
            } else if (pct >= 60) {
                lines.add(resolve(cfg, "coaching-insights.templates.damage-high", pct, self, ping));
            }
        }

        if (self.totemsPopped() >= 3) {
            lines.add(t(cfg, "coaching-insights.templates.totems-high")
                    .replace("<count>", String.valueOf(self.totemsPopped())));
        } else if (self.totemsPopped() <= 1 && opp.totemsPopped() >= 2) {
            lines.add(t(cfg, "coaching-insights.templates.totems-low")
                    .replace("<count>", String.valueOf(self.totemsPopped())));
        }

        if (match.gamemode().equalsIgnoreCase("Crystal") && self.crystalsPlaced() < 2) {
            lines.add(t(cfg, "coaching-insights.templates.crystals-low")
                    .replace("<count>", String.valueOf(self.crystalsPlaced())));
        }

        int totalHits = self.hitsLanded() + opp.hitsLanded();
        if (totalHits >= 10) {
            int acc = (int) Math.round((double) self.hitsLanded() / totalHits * 100);
            if (acc < 40) {
                lines.add(t(cfg, "coaching-insights.templates.accuracy-low").replace("<pct>", String.valueOf(acc)));
            }
        }

        if (ping >= 120) {
            lines.add(t(cfg, "coaching-insights.templates.ping-high").replace("<ping>", String.valueOf(ping)));
        }

        return lines.stream().limit(max).toList();
    }

    private String resolve(FileConfiguration cfg, String path, int pct,
                           com.meranked.model.RankedMatch.MatchStats self, int ping) {
        return t(cfg, path).replace("<pct>", String.valueOf(pct))
                .replace("<count>", String.valueOf(self.totemsPopped()))
                .replace("<ping>", String.valueOf(ping));
    }

    private String t(FileConfiguration cfg, String path) {
        return cfg.getString(path, "");
    }
}
