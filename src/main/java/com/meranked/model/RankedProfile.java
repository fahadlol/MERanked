package com.meranked.model;

import java.util.UUID;

public final class RankedProfile {

    private final UUID uuid;
    private final String gamemode;
    private double rating;
    private double ratingDeviation;
    private double volatility;
    private String tier;
    private double peakRating;
    private String peakTier;
    private long peakDate;
    private double seasonPeakRating;
    private String seasonPeakTier;
    private boolean ranked;
    private int placementPlayed;
    private int placementWins;
    private int placementLosses;
    private int wins;
    private int losses;
    private int winStreak;
    private String bestWinOpponent;
    private String bestWinTier;
    private long lastPlayed;
    private boolean decayActive;
    private int rankProtection;
    private int seasonId;
    // Placement difficulty tracking
    private double placementOpponentRatingSum;
    private double worstPlacementLossRating;
    private String placementCapOverride;
    // Upset tracking
    private int upsetWins;
    private double highestBeatenRating;
    private int lossStreak;

    public RankedProfile(UUID uuid, String gamemode) {
        this.uuid = uuid;
        this.gamemode = gamemode;
        this.rating = 1500;
        this.ratingDeviation = 350;
        this.volatility = 0.06;
        this.tier = "#0";
        this.peakRating = 1500;
        this.peakTier = "#0";
        this.peakDate = System.currentTimeMillis();
        this.seasonPeakRating = 1500;
        this.seasonPeakTier = "#0";
        this.ranked = false;
        this.placementPlayed = 0;
        this.placementWins = 0;
        this.placementLosses = 0;
        this.wins = 0;
        this.losses = 0;
        this.winStreak = 0;
        this.lastPlayed = 0;
        this.decayActive = false;
        this.rankProtection = 0;
        this.seasonId = 1;
    }

    public UUID uuid() { return uuid; }
    public String gamemode() { return gamemode; }
    public double rating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public double ratingDeviation() { return ratingDeviation; }
    public void setRatingDeviation(double ratingDeviation) { this.ratingDeviation = ratingDeviation; }
    public double volatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }
    public String tier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public double peakRating() { return peakRating; }
    public void setPeakRating(double peakRating) { this.peakRating = peakRating; }
    public String peakTier() { return peakTier; }
    public void setPeakTier(String peakTier) { this.peakTier = peakTier; }
    public long peakDate() { return peakDate; }
    public void setPeakDate(long peakDate) { this.peakDate = peakDate; }
    public double seasonPeakRating() { return seasonPeakRating; }
    public void setSeasonPeakRating(double seasonPeakRating) { this.seasonPeakRating = seasonPeakRating; }
    public String seasonPeakTier() { return seasonPeakTier; }
    public void setSeasonPeakTier(String seasonPeakTier) { this.seasonPeakTier = seasonPeakTier; }
    public boolean ranked() { return ranked; }
    public void setRanked(boolean ranked) { this.ranked = ranked; }
    public int placementPlayed() { return placementPlayed; }
    public void setPlacementPlayed(int placementPlayed) { this.placementPlayed = placementPlayed; }
    public int placementWins() { return placementWins; }
    public void setPlacementWins(int placementWins) { this.placementWins = placementWins; }
    public int placementLosses() { return placementLosses; }
    public void setPlacementLosses(int placementLosses) { this.placementLosses = placementLosses; }
    public int wins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int losses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int winStreak() { return winStreak; }
    public void setWinStreak(int winStreak) { this.winStreak = winStreak; }
    public String bestWinOpponent() { return bestWinOpponent; }
    public void setBestWinOpponent(String bestWinOpponent) { this.bestWinOpponent = bestWinOpponent; }
    public String bestWinTier() { return bestWinTier; }
    public void setBestWinTier(String bestWinTier) { this.bestWinTier = bestWinTier; }
    public long lastPlayed() { return lastPlayed; }
    public void setLastPlayed(long lastPlayed) { this.lastPlayed = lastPlayed; }
    public boolean decayActive() { return decayActive; }
    public void setDecayActive(boolean decayActive) { this.decayActive = decayActive; }
    public int rankProtection() { return rankProtection; }
    public void setRankProtection(int rankProtection) { this.rankProtection = rankProtection; }
    public int seasonId() { return seasonId; }
    public void setSeasonId(int seasonId) { this.seasonId = seasonId; }
    public double placementOpponentRatingSum() { return placementOpponentRatingSum; }
    public void addPlacementOpponentRating(double rating) { this.placementOpponentRatingSum += rating; }
    public double averagePlacementOpponentRating() {
        return placementPlayed == 0 ? 1500 : placementOpponentRatingSum / placementPlayed;
    }
    public double worstPlacementLossRating() { return worstPlacementLossRating; }
    public void setWorstPlacementLossRating(double v) { this.worstPlacementLossRating = v; }
    public String placementCapOverride() { return placementCapOverride; }
    public void setPlacementCapOverride(String placementCapOverride) { this.placementCapOverride = placementCapOverride; }
    public int upsetWins() { return upsetWins; }
    public void setUpsetWins(int upsetWins) { this.upsetWins = upsetWins; }
    public double highestBeatenRating() { return highestBeatenRating; }
    public void setHighestBeatenRating(double highestBeatenRating) { this.highestBeatenRating = highestBeatenRating; }
    public int placementNetWins() { return placementWins - placementLosses; }
    public int lossStreak() { return lossStreak; }
    public void setLossStreak(int lossStreak) { this.lossStreak = lossStreak; }

    /** Human-readable streak label, e.g. "5 Win Streak" or "2 Loss Streak". */
    public String streakLabel() {
        if (winStreak > 0) return winStreak + " Win Streak";
        if (lossStreak > 0) return lossStreak + " Loss Streak";
        return "No Streak";
    }

    public boolean inPlacements(int required) {
        return !ranked && placementPlayed < required;
    }
}
