package com.meranked.placeholders;

import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.model.QueueEntry;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import com.meranked.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class PlaceholderBridge {

    private final ServiceRegistry services;

    public PlaceholderBridge(ServiceRegistry services) {
        this.services = services;
    }

    public String resolve(Player player, String params) {
        if (player == null) return "";
        UUID uuid = player.getUniqueId();

        if (params.equals("region")) {
            return services.regions().displayRegion(player);
        }
        if (params.equals("season")) {
            return String.valueOf(services.seasons().currentSeasonId());
        }
        if (params.equals("ping")) {
            return String.valueOf(player.getPing());
        }
        if (params.equals("queue_status")) {
            return services.queue().isQueued(uuid) ? "In Queue" : "Idle";
        }
        if (params.equals("queue_time")) {
            return services.queue().getEntry(uuid).map(e -> String.valueOf(e.queueTimeSeconds())).orElse("0");
        }
        if (params.equals("queue_range")) {
            return services.queue().getEntry(uuid)
                    .map(e -> String.valueOf(services.matchmaking().getRatingRange(e.queueTimeSeconds())))
                    .orElse("100");
        }
        if (params.equals("current_gamemode")) {
            return services.queue().getGamemode(uuid).orElse(
                    services.matches().getMatch(uuid).map(RankedMatch::gamemode).orElse("")
            );
        }

        Optional<RankedMatch> match = services.matches().getMatch(uuid);
        if (params.equals("match_opponent")) {
            return match.map(m -> Bukkit.getOfflinePlayer(m.opponent(uuid)).getName()).orElse("");
        }
        if (params.equals("match_time")) {
            return match.map(m -> com.meranked.util.TextUtil.formatMatchTime(m.durationMillis())).orElse("0:00");
        }

        if (params.startsWith("progress_bar_")) {
            RankedProfile p = profile(uuid, params.substring(13)).orElse(null);
            return p == null ? "" : com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(p));
        }
        if (params.startsWith("next_tier_")) {
            RankedProfile p = profile(uuid, params.substring(10)).orElse(null);
            return p == null ? "-" : services.rankProgress().nextTierId(p);
        }
        if (params.startsWith("rating_to_next_")) {
            RankedProfile p = profile(uuid, params.substring(15)).orElse(null);
            return p == null ? "0" : String.valueOf(services.rankProgress().ratingToNext(p));
        }
        if (params.startsWith("rank_")) {
            String gamemode = params.substring(5);
            if (!services.profiles().enabledGamemodes().contains(gamemode)) return "-";
            RankedProfile p = services.profiles().getCachedProfile(uuid, gamemode);
            if (p == null || !p.ranked()) return "-";
            int rank = services.leaderboard().getRankCached(uuid, gamemode);
            return rank <= 0 ? "-" : "#" + rank;
        }
        if (params.startsWith("tier_")) {
            return profile(uuid, params.substring(5)).map(services.placements()::displayTier).orElse("#0");
        }
        if (params.startsWith("rating_")) {
            RankedProfile p = profile(uuid, params.substring(7)).orElse(null);
            if (p == null) return "1500";
            if (p.inPlacements(services.profiles().placementRequired(p.gamemode()))) return "Hidden";
            return String.valueOf(Math.round(p.rating()));
        }
        if (params.startsWith("peak_tier_")) {
            return profile(uuid, params.substring(10)).map(RankedProfile::peakTier).orElse("#0");
        }
        if (params.startsWith("peak_rating_")) {
            return profile(uuid, params.substring(12)).map(p -> String.valueOf(Math.round(p.peakRating()))).orElse("1500");
        }
        if (params.startsWith("season_peak_tier_")) {
            return profile(uuid, params.substring(17)).map(RankedProfile::seasonPeakTier).orElse("#0");
        }
        if (params.startsWith("confidence_")) {
            return profile(uuid, params.substring(11))
                    .map(p -> services.tiers().getConfidenceLabel(p.ratingDeviation())).orElse("Calibrating");
        }
        if (params.startsWith("wins_")) {
            return profile(uuid, params.substring(5)).map(p -> String.valueOf(p.wins())).orElse("0");
        }
        if (params.startsWith("losses_")) {
            return profile(uuid, params.substring(7)).map(p -> String.valueOf(p.losses())).orElse("0");
        }
        if (params.startsWith("placements_")) {
            RankedProfile p = profile(uuid, params.substring(11)).orElse(null);
            if (p == null) return "0/7";
            int req = services.profiles().placementRequired(p.gamemode());
            return p.placementPlayed() + "/" + req;
        }
        if (params.equals("win_gain") || params.equals("loss_loss")) {
            return match.map(m -> {
                RankedProfile self = services.profiles().getCachedProfile(uuid, m.gamemode());
                RankedProfile opp = services.profiles().getCachedProfile(m.opponent(uuid), m.gamemode());
                if (self == null || opp == null) return "0";
                RatingService.PotentialChange pot = services.rating().calculatePotential(self, opp);
                return params.equals("win_gain") ? String.valueOf(pot.winGain()) : String.valueOf(pot.lossLoss());
            }).orElse("0");
        }
        return null;
    }

    private Optional<RankedProfile> profile(UUID uuid, String gamemode) {
        if (!services.profiles().enabledGamemodes().contains(gamemode)) return Optional.empty();
        RankedProfile cached = services.profiles().getCachedProfile(uuid, gamemode);
        if (cached != null) return Optional.of(cached);
        services.profiles().loadProfileAsync(uuid, gamemode);
        return Optional.empty();
    }
}
