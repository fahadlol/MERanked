package com.meranked.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankedMatch {

    public enum State {
        VOTING, CINEMATIC, COUNTDOWN, ACTIVE, ENDING, FINISHED
    }

    private final String matchId;
    private final String gamemode;
    private final UUID player1;
    private final UUID player2;
    private final long startedAt;
    private State state;
    private String arenaName;
    private int cloneIndex;
    private UUID winner;
    private UUID loser;
    private final Map<UUID, Double> ratingBefore = new ConcurrentHashMap<>();
    private final Map<UUID, Double> ratingAfter = new ConcurrentHashMap<>();
    private final Map<UUID, String> tierBefore = new ConcurrentHashMap<>();
    private final Map<UUID, String> tierAfter = new ConcurrentHashMap<>();
    private final Map<UUID, MatchStats> stats = new ConcurrentHashMap<>();
    private boolean ratingApplied;
    private boolean noRatingChange;
    private String noRatingReason;
    private boolean dodge;
    private final List<UUID> spectators = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<UUID> staffSpectators = new java.util.concurrent.CopyOnWriteArrayList<>();
    // Best of 3
    private boolean bestOfThree;
    private int currentRound = 1;
    private final Map<UUID, Integer> roundWins = new ConcurrentHashMap<>();
    // Match quality
    private int matchQuality;
    private String matchQualityReason;
    private int queueRange;
    private String upsetLevel;
    private String matchmakingReason;
    // Lifecycle guards
    private boolean hasStarted;   // true once the first round has gone live (FIGHT shown)
    private boolean finalized;    // true once the match result has been processed exactly once

    public RankedMatch(String matchId, String gamemode, UUID player1, UUID player2) {
        this.matchId = matchId;
        this.gamemode = gamemode;
        this.player1 = player1;
        this.player2 = player2;
        this.startedAt = System.currentTimeMillis();
        this.state = State.VOTING;
        stats.put(player1, new MatchStats());
        stats.put(player2, new MatchStats());
    }

    public String matchId() { return matchId; }
    public String gamemode() { return gamemode; }
    public UUID player1() { return player1; }
    public UUID player2() { return player2; }
    public long startedAt() { return startedAt; }
    public State state() { return state; }
    public void setState(State state) { this.state = state; }
    public String arenaName() { return arenaName; }
    public void setArenaName(String arenaName) { this.arenaName = arenaName; }
    public int cloneIndex() { return cloneIndex; }
    public void setCloneIndex(int cloneIndex) { this.cloneIndex = cloneIndex; }
    public UUID winner() { return winner; }
    public void setWinner(UUID winner) { this.winner = winner; }
    public UUID loser() { return loser; }
    public void setLoser(UUID loser) { this.loser = loser; }
    public Map<UUID, Double> ratingBefore() { return ratingBefore; }
    public Map<UUID, Double> ratingAfter() { return ratingAfter; }
    public Map<UUID, String> tierBefore() { return tierBefore; }
    public Map<UUID, String> tierAfter() { return tierAfter; }
    public MatchStats stats(UUID uuid) { return stats.computeIfAbsent(uuid, k -> new MatchStats()); }
    public Map<UUID, MatchStats> allStats() { return stats; }
    public boolean ratingApplied() { return ratingApplied; }
    public void setRatingApplied(boolean ratingApplied) { this.ratingApplied = ratingApplied; }
    public boolean noRatingChange() { return noRatingChange; }
    public void setNoRatingChange(boolean noRatingChange) { this.noRatingChange = noRatingChange; }
    public String noRatingReason() { return noRatingReason; }
    public void setNoRatingReason(String noRatingReason) { this.noRatingReason = noRatingReason; }
    public boolean dodge() { return dodge; }
    public void setDodge(boolean dodge) { this.dodge = dodge; }
    public List<UUID> spectators() { return spectators; }
    public List<UUID> staffSpectators() { return staffSpectators; }
    public boolean bestOfThree() { return bestOfThree; }
    public void setBestOfThree(boolean bestOfThree) { this.bestOfThree = bestOfThree; }
    public int currentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public int roundWins(UUID uuid) { return roundWins.getOrDefault(uuid, 0); }
    public void addRoundWin(UUID uuid) { roundWins.merge(uuid, 1, Integer::sum); }
    public int matchQuality() { return matchQuality; }
    public void setMatchQuality(int matchQuality) { this.matchQuality = matchQuality; }
    public String matchQualityReason() { return matchQualityReason; }
    public void setMatchQualityReason(String matchQualityReason) { this.matchQualityReason = matchQualityReason; }
    public int queueRange() { return queueRange; }
    public void setQueueRange(int queueRange) { this.queueRange = queueRange; }
    public String upsetLevel() { return upsetLevel; }
    public void setUpsetLevel(String upsetLevel) { this.upsetLevel = upsetLevel; }
    public String matchmakingReason() { return matchmakingReason; }
    public void setMatchmakingReason(String matchmakingReason) { this.matchmakingReason = matchmakingReason; }
    public boolean hasStarted() { return hasStarted; }
    public void setHasStarted(boolean hasStarted) { this.hasStarted = hasStarted; }
    /** Returns true only for the first caller; subsequent calls return false (idempotency guard). */
    public boolean markFinalized() {
        if (finalized) return false;
        finalized = true;
        return true;
    }

    public UUID opponent(UUID player) {
        return player.equals(player1) ? player2 : player1;
    }

    public long durationMillis() {
        return System.currentTimeMillis() - startedAt;
    }

    public static final class MatchStats {
        private double damageDealt;
        private int totemsPopped;
        private int crystalsPlaced;
        private int hitsLanded;
        private int criticalHits;

        public double damageDealt() { return damageDealt; }
        public void addDamage(double amount) { this.damageDealt += amount; }
        public int totemsPopped() { return totemsPopped; }
        public void incrementTotems() { this.totemsPopped++; }
        public int crystalsPlaced() { return crystalsPlaced; }
        public void incrementCrystals() { this.crystalsPlaced++; }
        public int hitsLanded() { return hitsLanded; }
        public void incrementHits() { this.hitsLanded++; }
        public int criticalHits() { return criticalHits; }
        public void incrementCriticals() { this.criticalHits++; }
    }
}
