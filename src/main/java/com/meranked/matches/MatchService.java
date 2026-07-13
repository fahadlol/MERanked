package com.meranked.matches;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.model.Arena;
import com.meranked.model.AlertSeverity;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import com.meranked.rating.AntiBoostService;
import com.meranked.rating.PlacementService;
import com.meranked.rating.ProfileService;
import com.meranked.rating.RatingService;
import com.meranked.rating.TierService;
import com.meranked.rating.UpsetService;
import com.meranked.util.IdUtil;
import com.meranked.util.PlayerFreezeUtil;
import com.meranked.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchService {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;
    private final Map<String, RankedMatch> activeMatches = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerMatchMap = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();

    public MatchService(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void createMatch(String gamemode, UUID player1, UUID player2) {
        createMatch(gamemode, player1, player2, 0);
    }

    public void createMatch(String gamemode, UUID player1, UUID player2, int queueRange) {
        createMatch(gamemode, player1, player2, queueRange, null);
    }

    public void createMatch(String gamemode, UUID player1, UUID player2, int queueRange, String matchmakingReason) {
        String matchId = IdUtil.newMatchId();
        RankedMatch match = new RankedMatch(matchId, gamemode, player1, player2);
        match.setQueueRange(queueRange);
        match.setMatchmakingReason(matchmakingReason);
        activeMatches.put(matchId, match);
        playerMatchMap.put(player1, matchId);
        playerMatchMap.put(player2, matchId);

        services.behaviorFingerprint().startMatch(player1, matchId);
        services.behaviorFingerprint().startMatch(player2, matchId);

        ProfileService profiles = services.profiles();
        RankedProfile p1 = profiles.getProfile(player1, gamemode);
        RankedProfile p2 = profiles.getProfile(player2, gamemode);
        match.ratingBefore().put(player1, p1.rating());
        match.ratingBefore().put(player2, p2.rating());
        match.tierBefore().put(player1, p1.tier());
        match.tierBefore().put(player2, p2.tier());
        match.setBestOfThree(shouldUseBestOfThree(p1, p2));

        Player bp1 = Bukkit.getPlayer(player1);
        Player bp2 = Bukkit.getPlayer(player2);
        if (bp1 != null) {
            returnLocations.put(player1, bp1.getLocation());
            services.messages().sendPrefixed(bp1, "queue.match-found");
        }
        if (bp2 != null) services.messages().sendPrefixed(bp2, "queue.match-found");

        services.kits().preloadAsync(player1, gamemode);
        services.kits().preloadAsync(player2, gamemode);

        match.setState(RankedMatch.State.VOTING);
        services.arenaVoting().startVoting(match, arena -> {
            if (arena == null) {
                cancelMatch(match, "No arena available");
                return;
            }
            startMatchWithArena(match, arena);
        });
    }

    private void startMatchWithArena(RankedMatch match, Arena arena) {
        services.arenaVoting().endVoting(match);
        Optional<Integer> clone = services.arenas().reserveClone(arena.name());
        if (clone.isEmpty()) {
            services.arenas().autoDisableArena(arena, "Clone failed");
            cancelMatch(match, "No arena clone available");
            return;
        }

        match.setArenaName(arena.name());
        match.setCloneIndex(clone.get());
        arena.incrementUsage();
        services.arenaLog().logArenaSelected(arena.name(), match.matchId(), match.gamemode());

        services.arenas().regenerateArenaAsync(arena, clone.get(), () -> {
            services.cinematic().playIntro(match, services.profiles(), services.placements(), () -> {
                teleportToSpawns(match, arena, clone.get());
                startCountdown(match);
            });
        });
    }

    private void teleportToSpawns(RankedMatch match, Arena arena, int cloneIndex) {
        Player p1 = Bukkit.getPlayer(match.player1());
        Player p2 = Bukkit.getPlayer(match.player2());
        Location s1 = services.arenas().offsetLocation(arena.spawn1(), arena.name(), cloneIndex);
        Location s2 = services.arenas().offsetLocation(arena.spawn2(), arena.name(), cloneIndex);

        if (p1 != null) {
            p1.teleport(s1);
            applyKit(p1, match.gamemode());
        }
        if (p2 != null) {
            p2.teleport(s2);
            applyKit(p2, match.gamemode());
        }
        match.setState(RankedMatch.State.COUNTDOWN);
    }

    private void applyKit(Player player, String gamemode) {
        services.kits().applyKit(player, gamemode);
    }

    private void startCountdown(RankedMatch match) {
        FileConfiguration config = services.config().get("config.yml");
        warnPreMatchDemotion(match);
        int seconds = config.getInt("match.countdown-seconds", 3);
        countdownTick(match, seconds);
    }

    private void warnPreMatchDemotion(RankedMatch match) {
        FileConfiguration demCfg = services.config().get("demotion.yml");
        if (!demCfg.getBoolean("demotion.enabled", true) || !demCfg.getBoolean("demotion.warn-before-match", true)) return;
        for (UUID uuid : List.of(match.player1(), match.player2())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            RankedProfile profile = services.profiles().getProfile(uuid, match.gamemode());
            if (profile.inPlacements(services.profiles().placementRequired(match.gamemode()))) continue;
            if (services.rankProgress().atDemotionRisk(profile)) {
                String lower = services.rankProgress().nextLowerTierId(profile.tier());
                TextUtil.title(player,
                        demCfg.getString("demotion.pre-match-title", "DEMOTION RISK"),
                        demCfg.getString("demotion.pre-match-subtitle", "").replace("<next_lower>", lower),
                        10, 50, 10);
            }
        }
    }

    private void countdownTick(RankedMatch match, int remaining) {
        if (match.state() == RankedMatch.State.FINISHED || !activeMatches.containsKey(match.matchId())) return;
        if (remaining <= 0) {
            match.setState(RankedMatch.State.ACTIVE);
            match.setHasStarted(true);
            services.matchLog().logMatchStart(match, match.arenaName());
            for (UUID uuid : List.of(match.player1(), match.player2())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    TextUtil.title(player, services.messages().raw("match.fight"), "", 0, 20, 5);
                }
            }
            return;
        }
        for (UUID uuid : List.of(match.player1(), match.player2())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                TextUtil.title(player,
                        services.messages().raw("match.countdown").replace("<count>", String.valueOf(remaining)),
                        "", 0, 20, 0);
            }
        }
        plugin.tasks().runSyncLater(() -> countdownTick(match, remaining - 1), 20L);
    }

    public void handleDeath(Player victim, Player killer) {
        Optional<RankedMatch> matchOpt = getMatch(victim.getUniqueId());
        if (matchOpt.isEmpty() || matchOpt.get().state() != RankedMatch.State.ACTIVE) return;
        RankedMatch match = matchOpt.get();
        UUID roundWinner = killer != null ? killer.getUniqueId() : match.opponent(victim.getUniqueId());

        if (match.bestOfThree()) {
            handleRoundEnd(match, roundWinner, victim.getUniqueId());
            return;
        }
        endMatch(match, roundWinner, victim.getUniqueId(), false);
    }

    private boolean shouldUseBestOfThree(RankedProfile p1, RankedProfile p2) {
        FileConfiguration cfg = services.config().get("best-of-three.yml");
        if (!cfg.getBoolean("best-of-three.enabled", true)) return false;
        String minTier = cfg.getString("best-of-three.minimum-tier", "HT3");
        return meetsTier(p1.tier(), minTier) || meetsTier(p2.tier(), minTier);
    }

    private boolean meetsTier(String tier, String minTier) {
        if (tier == null || tier.equals("#0")) return false;
        return tierIndex(tier) >= tierIndex(minTier);
    }

    private void handleRoundEnd(RankedMatch match, UUID roundWinner, UUID roundLoser) {
        FileConfiguration cfg = services.config().get("best-of-three.yml");
        int toWin = cfg.getInt("best-of-three.rounds-to-win", 2);
        match.addRoundWin(roundWinner);
        match.setState(RankedMatch.State.ENDING);

        int wWins = match.roundWins(roundWinner);
        int lWins = match.roundWins(roundLoser);

        // Announce round score
        for (UUID uuid : List.of(match.player1(), match.player2())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            int myScore = match.roundWins(uuid);
            int oppScore = match.roundWins(match.opponent(uuid));
            TextUtil.title(player,
                    services.messages().raw("match.round-title").replace("<round>", String.valueOf(match.currentRound())),
                    services.messages().raw("match.round-subtitle")
                            .replace("<player_score>", String.valueOf(myScore))
                            .replace("<opponent_score>", String.valueOf(oppScore)),
                    5, 40, 10);
        }

        if (wWins >= toWin) {
            match.setState(RankedMatch.State.ACTIVE);
            endMatch(match, roundWinner, roundLoser, false);
            return;
        }

        // Start next round
        match.setCurrentRound(match.currentRound() + 1);
        boolean resetArena = cfg.getBoolean("best-of-three.reset-arena-between-rounds", true);
        plugin.tasks().runSyncLater(() -> startNextRound(match, resetArena), 60L);
    }

    private void startNextRound(RankedMatch match, boolean resetArena) {
        if (!activeMatches.containsKey(match.matchId())) return;
        services.arenas().getArena(match.arenaName()).ifPresentOrElse(arena -> {
            Runnable begin = () -> {
                teleportToSpawns(match, arena, match.cloneIndex());
                for (UUID uuid : List.of(match.player1(), match.player2())) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                }
                startCountdown(match);
            };
            if (resetArena) {
                services.arenas().regenerateArenaAsync(arena, match.cloneIndex(), begin);
            } else {
                begin.run();
            }
        }, () -> endMatch(match, match.player1(), match.player2(), false));
    }

    public void handleDisconnect(Player player) {
        getMatch(player.getUniqueId()).ifPresent(match -> {
            // Once the match has gone live (including between Best-of-3 rounds), a disconnect is a full loss.
            // Before it starts, it is treated as a dodge and the match is cancelled.
            if (match.hasStarted()) {
                endMatch(match, match.opponent(player.getUniqueId()), player.getUniqueId(), true);
            } else {
                services.antiDodge().recordDodge(player.getUniqueId());
                cancelMatch(match, "Disconnect before start");
            }
        });
    }

    public void handleLeaveDuringMatch(Player player) {
        getMatch(player.getUniqueId()).ifPresent(match -> {
            if (match.state() == RankedMatch.State.VOTING || match.state() == RankedMatch.State.CINEMATIC
                    || match.state() == RankedMatch.State.COUNTDOWN) {
                services.antiDodge().recordDodge(player.getUniqueId());
                cancelMatch(match, "Dodge");
            }
        });
    }

    public void endMatch(RankedMatch match, UUID winner, UUID loser, boolean disconnect) {
        // Idempotency guard: process the result exactly once regardless of current state
        // (handles between-round ENDING state, simultaneous deaths, disconnect + death races).
        if (!match.markFinalized()) return;
        match.setState(RankedMatch.State.ENDING);
        match.setWinner(winner);
        match.setLoser(loser);

        Player wPlayer = Bukkit.getPlayer(winner);
        Player lPlayer = Bukkit.getPlayer(loser);
        String ip1 = wPlayer != null && wPlayer.getAddress() != null ? wPlayer.getAddress().getAddress().getHostAddress() : null;
        String ip2 = lPlayer != null && lPlayer.getAddress() != null ? lPlayer.getAddress().getAddress().getHostAddress() : null;

        AntiBoostService.ValidationResult validation = services.antiBoost().validate(
                match.player1(), match.player2(), ip1, ip2, match.durationMillis());

        if (!validation.allowed()) {
            match.setNoRatingChange(true);
            match.setNoRatingReason(validation.reason());
            services.alerts().createAlert("SAME_IP_MATCH".equals(validation.reason()) ? "SAME_IP_MATCH" : "SHORT_MATCH",
                    AlertSeverity.MEDIUM, validation.reason(), match, List.of(winner, loser));
        }

        computeMatchQuality(match);

        services.behaviorFingerprint().endMatch(match.player1(), match);
        services.behaviorFingerprint().endMatch(match.player2(), match);

        if (!match.noRatingChange()) {
            applyRating(match, winner, loser);
            services.antiBoost().recordOpponentMatch(match.player1(), match.player2());
        }
        services.detection().recordMatch(match.player1(), match.player2(), match.gamemode());

        showResults(match, winner, loser);
        services.replays().saveMatch(match);
        saveMatchAsync(match);
        saveParticipantsAsync(match);
        cleanupMatch(match);

        java.util.Map<UUID, Double> ratingChanges = new java.util.HashMap<>();
        if (!match.noRatingChange()) {
            for (UUID id : List.of(winner, loser)) {
                double before = match.ratingBefore().getOrDefault(id, 0.0);
                double after = match.ratingAfter().getOrDefault(id, before);
                ratingChanges.put(id, after - before);
            }
        }
        services.matchLog().logMatchEnd(match, winner, loser,
                match.bestOfThree() ? match.roundWins(winner) + "-" + match.roundWins(loser) : "1-0",
                match.durationMillis(), ratingChanges);
        if (disconnect) {
            services.matchLog().logMatchDisconnect(match.matchId(), loser, "disconnect");
            if (lPlayer != null) services.messages().send(lPlayer, "match.disconnect-loss");
        }
    }

    private void applyRating(RankedMatch match, UUID winner, UUID loser) {
        ProfileService profiles = services.profiles();
        RatingService rating = services.rating();
        TierService tiers = services.tiers();
        PlacementService placements = services.placements();
        FileConfiguration config = services.config().get("config.yml");

        RankedProfile winProfile = profiles.getProfile(winner, match.gamemode());
        RankedProfile loseProfile = profiles.getProfile(loser, match.gamemode());

        double winnerRatingBefore = winProfile.rating();
        double loserRatingBefore = loseProfile.rating();
        RatingService.RatingUpdate winUpdate = rating.calculate(winProfile, loseProfile, 1.0);
        RatingService.RatingUpdate loseUpdate = rating.calculate(loseProfile, winProfile, 0.0);

        // Underdog / upset bonus
        UpsetService.UpsetLevel upset = UpsetService.UpsetLevel.NONE;
        double upsetBonus = 0;
        if (services.upsets().enabled() && !winProfile.inPlacements(profiles.placementRequired(match.gamemode()))) {
            upset = services.upsets().classify(winnerRatingBefore, loserRatingBefore);
            upsetBonus = services.upsets().bonusRating(upset, winUpdate.change());
        }

        winProfile.setRating(winUpdate.rating() + upsetBonus + services.hiddenMmr().catchUpBonus(winProfile));
        winProfile.setRatingDeviation(winUpdate.rd());
        winProfile.setVolatility(winUpdate.volatility());

        if (upset != UpsetService.UpsetLevel.NONE) {
            double diff = loserRatingBefore - winnerRatingBefore;
            services.upsets().recordUpset(winner, match.gamemode(), loserRatingBefore, diff);
            winProfile.setUpsetWins(winProfile.upsetWins() + 1);
            if (loserRatingBefore > winProfile.highestBeatenRating()) {
                winProfile.setHighestBeatenRating(loserRatingBefore);
            }
            match.setUpsetLevel(upset.name());
        }
        loseProfile.setRating(loseUpdate.rating());
        loseProfile.setRatingDeviation(loseUpdate.rd());
        loseProfile.setVolatility(loseUpdate.volatility());

        int required = profiles.placementRequired(match.gamemode());
        Player wPlayer = Bukkit.getPlayer(winner);
        Player lPlayer = Bukkit.getPlayer(loser);
        int winPing = wPlayer == null ? 0 : wPlayer.getPing();
        int losePing = lPlayer == null ? 0 : lPlayer.getPing();

        if (winProfile.inPlacements(required)) {
            services.placementBehavior().recordPlacementMatch(winProfile, match.stats(winner),
                    match.stats(loser), winPing, true);
            placements.recordPlacementMatch(winProfile, true,
                    Bukkit.getOfflinePlayer(loser).getName(), loseProfile.tier(), loseProfile.rating());
            if (placements.completePlacementsIfReady(winProfile)) {
                showPlacementRecap(Bukkit.getPlayer(winner), winProfile);
            }
        } else {
            winProfile.setWins(winProfile.wins() + 1);
            winProfile.setWinStreak(winProfile.winStreak() + 1);
            winProfile.setLossStreak(0);
            announceHotStreak(winner, winProfile);
            String oldTier = winProfile.tier();
            String newTier = tiers.getTierForRating(winProfile.rating(), false);
            if (profiles.canDemote(winProfile, newTier, config) || tierIndex(newTier) >= tierIndex(oldTier)) {
                winProfile.setTier(newTier);
            }
            profiles.applyRankProtection(winProfile, oldTier, newTier, config);
            profiles.updatePeak(winProfile);
        }

        if (loseProfile.inPlacements(required)) {
            services.placementBehavior().recordPlacementMatch(loseProfile, match.stats(loser),
                    match.stats(winner), losePing, false);
            placements.recordPlacementMatch(loseProfile, false, null, null, winProfile.rating());
            placements.completePlacementsIfReady(loseProfile);
        } else {
            loseProfile.setLosses(loseProfile.losses() + 1);
            loseProfile.setWinStreak(0);
            loseProfile.setLossStreak(loseProfile.lossStreak() + 1);
            profiles.decreaseRankProtection(loseProfile, true, config);
            String oldTier = loseProfile.tier();
            String newTier = tiers.getTierForRating(loseProfile.rating(), false);
            if (tiers.wouldDemote(oldTier, loseProfile.rating()) && profiles.canDemote(loseProfile, newTier, config)) {
                int demoteRating = tiers.demotionRating(oldTier, loseProfile.rating());
                if (loseProfile.rating() < demoteRating) {
                    newTier = tiers.getTierForRating(loseProfile.rating(), false);
                    loseProfile.setTier(newTier);
                }
            }
        }

        winProfile.setLastPlayed(System.currentTimeMillis());
        loseProfile.setLastPlayed(System.currentTimeMillis());
        match.ratingAfter().put(winner, winProfile.rating());
        match.ratingAfter().put(loser, loseProfile.rating());
        match.tierAfter().put(winner, winProfile.tier());
        match.tierAfter().put(loser, loseProfile.tier());
        match.setRatingApplied(true);

        profiles.queueSave(winProfile);
        profiles.queueSave(loseProfile);

        if (!winProfile.inPlacements(required)) {
            services.hiddenMmr().afterRatingChange(winProfile, winnerRatingBefore, winProfile.rating(), true);
        }
        if (!loseProfile.inPlacements(required)) {
            services.hiddenMmr().afterRatingChange(loseProfile, loserRatingBefore, loseProfile.rating(), false);
        }

        services.suspicion().checkRatingSpike(winner, winUpdate.change());
    }

    private void announceHotStreak(UUID uuid, RankedProfile profile) {
        FileConfiguration cfg = services.config().get("streak-pressure.yml");
        if (!cfg.getBoolean("streak-pressure.enabled", true)) return;
        int at = cfg.getInt("streak-pressure.announce-at-streak", 5);
        if (at <= 0 || profile.winStreak() != at) return;
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        String msg = cfg.getString("streak-pressure.hot-streak-broadcast", "")
                .replace("<player>", name == null ? "?" : name)
                .replace("<streak>", String.valueOf(profile.winStreak()));
        var component = services.messages().format(msg);
        for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(component);
    }

    private void computeMatchQuality(RankedMatch match) {
        if (!services.matchQuality().enabled()) return;
        RankedProfile p1 = services.profiles().getProfile(match.player1(), match.gamemode());
        RankedProfile p2 = services.profiles().getProfile(match.player2(), match.gamemode());
        Player bp1 = Bukkit.getPlayer(match.player1());
        Player bp2 = Bukkit.getPlayer(match.player2());
        int ping1 = bp1 == null ? 0 : bp1.getPing();
        int ping2 = bp2 == null ? 0 : bp2.getPing();
        int recent = services.antiBoost().recentOpponentCount(match.player1(), match.player2());
        MatchQualityService.QualityResult result = services.matchQuality()
                .evaluate(match, p1, p2, ping1, ping2, recent);
        match.setMatchQuality(result.quality());
        match.setMatchQualityReason(result.reason());
        services.matchQuality().store(match.matchId(), result, match.matchmakingReason());
    }

    private void showPlacementRecap(Player player, RankedProfile profile) {
        if (player == null) return;
        TierService tiers = services.tiers();
        Map<String, String> ph = new HashMap<>();
        ph.put("gamemode", profile.gamemode());
        ph.put("wins", String.valueOf(profile.placementWins()));
        ph.put("losses", String.valueOf(profile.placementLosses()));
        ph.put("opponent", profile.bestWinOpponent() == null ? "N/A" : profile.bestWinOpponent());
        ph.put("opponent_tier", profile.bestWinTier() == null ? "N/A" : profile.bestWinTier());
        ph.put("avg_opponent", services.placements().averageOpponentTier(profile));
        ph.put("tier", profile.tier());
        ph.put("confidence", tiers.getConfidenceLabel(profile.ratingDeviation()));
        TextUtil.title(player, services.messages().raw("placement.recap-title"), "", 10, 60, 10);
        services.messages().sendPrefixed(player, "placement.recap", ph);
        services.gui().openPlacementRecap(player, profile);
    }

    private void showResults(RankedMatch match, UUID winner, UUID loser) {
        double winChange = match.ratingAfter().getOrDefault(winner, match.ratingBefore().get(winner))
                - match.ratingBefore().get(winner);
        double loseChange = match.ratingAfter().getOrDefault(loser, match.ratingBefore().get(loser))
                - match.ratingBefore().get(loser);

        Player w = Bukkit.getPlayer(winner);
        Player l = Bukkit.getPlayer(loser);
        if (w != null) {
            if (match.noRatingChange()) {
                services.messages().send(w, "match.no-rating-change");
            } else if (match.upsetLevel() != null) {
                TextUtil.title(w, services.upsets().upsetTitle(), services.upsets().upsetSubtitle(), 10, 50, 10);
                UpsetService.UpsetLevel level = UpsetService.UpsetLevel.valueOf(match.upsetLevel());
                w.sendMessage(services.messages().format(services.upsets().chatMessage(level, Math.round(winChange))));
            } else {
                TextUtil.title(w, services.messages().raw("match.victory-title"),
                        services.messages().raw("match.victory-subtitle")
                                .replace("<rating_change>", String.valueOf(Math.round(winChange))), 10, 40, 10);
            }
            sendMatchSummary(w, match, winner, winChange);
            sendProgressAndDemotion(w, winner, match.gamemode());
            sendCoachingInsights(w, match, winner);
        }
        if (l != null) {
            if (!match.noRatingChange()) {
                TextUtil.title(l, services.messages().raw("match.defeat-title"),
                        services.messages().raw("match.defeat-subtitle")
                                .replace("<rating_change>", String.valueOf(Math.round(Math.abs(loseChange)))), 10, 40, 10);
            }
            sendMatchSummary(l, match, loser, loseChange);
            sendProgressAndDemotion(l, loser, match.gamemode());
            sendCoachingInsights(l, match, loser);
        }
    }

    private void sendCoachingInsights(Player player, RankedMatch match, UUID uuid) {
        if (player == null || !services.coachingInsights().enabled()) return;
        FileConfiguration cfg = services.config().get("coaching-insights.yml");
        if (!cfg.getBoolean("coaching-insights.show-in-chat", true)) return;
        int ping = player.getPing();
        for (String line : services.coachingInsights().insights(match, uuid, ping)) {
            if (line == null || line.isBlank()) continue;
            player.sendMessage(services.messages().format(line));
        }
    }

    private void sendProgressAndDemotion(Player player, UUID uuid, String gamemode) {
        if (player == null) return;
        RankedProfile profile = services.profiles().getProfile(uuid, gamemode);
        if (profile.inPlacements(services.profiles().placementRequired(gamemode))) return;

        services.rankProgress().sendChatProgress(player, profile);

        FileConfiguration progressCfg = services.config().get("rank-progress.yml");
        if (progressCfg.getBoolean("rank-progress.show-in-actionbar", true)) {
            int seconds = progressCfg.getInt("rank-progress.actionbar-duration-seconds", 5);
            BukkitTask[] holder = new BukkitTask[1];
            long[] elapsed = {0};
            holder[0] = plugin.tasks().runSyncTimer(() -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && elapsed[0] < seconds) {
                    services.rankProgress().sendActionBarProgress(online, profile);
                }
                elapsed[0]++;
                if (elapsed[0] >= seconds && holder[0] != null) holder[0].cancel();
            }, 0L, 20L);
        }

        // Demotion warning
        FileConfiguration demCfg = services.config().get("demotion.yml");
        if (demCfg.getBoolean("demotion.enabled", true) && services.rankProgress().atDemotionRisk(profile)) {
            if (demCfg.getBoolean("demotion.warn-in-chat", true)) {
                player.sendMessage(services.messages().format(
                        demCfg.getString("demotion.chat-warning", "").replace("<tier>", profile.tier())));
            }
            if (demCfg.getBoolean("demotion.warn-in-actionbar", true)) {
                player.sendActionBar(TextUtil.parse(demCfg.getString("demotion.actionbar", "")
                        .replace("<rating>", String.valueOf(Math.round(profile.rating())))
                        .replace("<safe>", String.valueOf(services.rankProgress().safeRating(profile)))));
            }
        }
    }

    private void sendMatchSummary(Player player, RankedMatch match, UUID uuid, double change) {
        var stats = match.stats(uuid);
        double oldR = match.ratingBefore().getOrDefault(uuid, 0.0);
        double newR = match.ratingAfter().getOrDefault(uuid, oldR);
        Map<String, String> ph = Map.of(
                "damage", String.format("%.1f", stats.damageDealt()),
                "totems", String.valueOf(stats.totemsPopped()),
                "crystals", String.valueOf(stats.crystalsPlaced()),
                "time", TextUtil.formatMatchTime(match.durationMillis()),
                "old_rating", String.valueOf(Math.round(oldR)),
                "new_rating", String.valueOf(Math.round(newR))
        );
        String raw = services.messages().raw("match.summary");
        for (String line : raw.split("\n")) {
            String resolved = line;
            for (var e : ph.entrySet()) resolved = resolved.replace("<" + e.getKey() + ">", e.getValue());
            player.sendMessage(TextUtil.parse(resolved));
        }
        if (match.matchQuality() > 0) {
            player.sendMessage(services.messages().format(services.messages().raw("match.match-quality")
                    .replace("<quality>", String.valueOf(match.matchQuality()))
                    .replace("<reason>", match.matchQualityReason() == null ? "" : match.matchQualityReason())));
        }
        if (match.matchmakingReason() != null && !match.matchmakingReason().isBlank()) {
            player.sendMessage(services.messages().format(services.messages().raw("match.matchmaking-info")
                    .replace("<info>", match.matchmakingReason())));
        }
    }

    private void saveParticipantsAsync(RankedMatch match) {
        services.database().executeAsync(conn -> {
            for (UUID uuid : List.of(match.player1(), match.player2())) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO ranked_match_participants
                    (match_id, uuid, rating_before, rating_after, tier_before, tier_after, rating_change, ping)
                    VALUES (?,?,?,?,?,?,?,?)
                    """)) {
                    double before = match.ratingBefore().getOrDefault(uuid, 0.0);
                    double after = match.ratingAfter().getOrDefault(uuid, before);
                    Player p = Bukkit.getPlayer(uuid);
                    ps.setString(1, match.matchId());
                    ps.setString(2, uuid.toString());
                    ps.setDouble(3, before);
                    ps.setDouble(4, after);
                    ps.setString(5, match.tierBefore().get(uuid));
                    ps.setString(6, match.tierAfter().get(uuid));
                    ps.setDouble(7, after - before);
                    ps.setInt(8, p == null ? 0 : p.getPing());
                    ps.executeUpdate();
                }
            }
        });
    }

    private void cleanupMatch(RankedMatch match) {
        match.setState(RankedMatch.State.FINISHED);
        services.spectating().removeAllSpectators(match);
        if (match.arenaName() != null) {
            services.arenas().releaseClone(match.arenaName(), match.cloneIndex());
            FileConfiguration arenas = services.config().get("arenas.yml");
            long delay = arenas.getLong("arenas.regeneration.regen-delay-seconds", 3) * 20;
            plugin.tasks().runSyncLater(() -> {
                services.arenas().getArena(match.arenaName()).ifPresent(arena ->
                        services.arenas().regenerateArenaAsync(arena, match.cloneIndex(), () -> {}));
            }, delay);
        }

        for (UUID uuid : List.of(match.player1(), match.player2())) {
            playerMatchMap.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Location ret = returnLocations.remove(uuid);
                if (ret != null) player.teleport(ret);
                else services.utility().teleportSpawn(player);
                player.setGameMode(GameMode.SURVIVAL);
                PlayerFreezeUtil.setFrozen(player, false);
            }
        }
        activeMatches.remove(match.matchId());
    }

    private void cancelMatch(RankedMatch match, String reason) {
        for (UUID uuid : List.of(match.player1(), match.player2())) {
            playerMatchMap.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerFreezeUtil.setFrozen(player, false);
                player.closeInventory();
                services.utility().teleportSpawn(player);
            }
        }
        services.arenaVoting().endVoting(match);
        activeMatches.remove(match.matchId());
        services.matchLog().logMatchCancel(match.matchId(), reason);
    }

    private void saveMatchAsync(RankedMatch match) {
        DatabaseService db = services.database();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_matches
                (match_id, gamemode, arena, winner, loser, duration, started_at, ended_at,
                 no_rating, no_rating_reason, dodge, season_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, match.matchId());
                ps.setString(2, match.gamemode());
                ps.setString(3, match.arenaName());
                ps.setString(4, match.winner().toString());
                ps.setString(5, match.loser().toString());
                ps.setLong(6, match.durationMillis());
                ps.setLong(7, match.startedAt());
                ps.setLong(8, System.currentTimeMillis());
                ps.setBoolean(9, match.noRatingChange());
                ps.setString(10, match.noRatingReason());
                ps.setBoolean(11, match.dodge());
                ps.setInt(12, services.seasons().currentSeasonId());
                ps.executeUpdate();
            }
        });
    }

    public Optional<RankedMatch> getMatch(UUID uuid) {
        String id = playerMatchMap.get(uuid);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(activeMatches.get(id));
    }

    public Optional<RankedMatch> getMatchById(String matchId) {
        return Optional.ofNullable(activeMatches.get(matchId));
    }

    public Collection<RankedMatch> liveMatches() {
        return activeMatches.values();
    }

    public boolean isInMatch(UUID uuid) {
        return playerMatchMap.containsKey(uuid);
    }

    public void shutdown() {
        new ArrayList<>(activeMatches.values()).forEach(m -> cancelMatch(m, "Shutdown"));
    }

    private int tierIndex(String tier) {
        return services.tiers().allTiers().stream().map(TierService.TierDefinition::id).toList().indexOf(tier);
    }
}
