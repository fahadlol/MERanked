package com.meranked.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RankedProfileTest {

    private RankedProfile profile() {
        return new RankedProfile(UUID.randomUUID(), "Crystal");
    }

    @Test
    void defaultsMatchGlickoBaseline() {
        RankedProfile p = profile();
        assertEquals(1500, p.rating(), 0.0001);
        assertEquals(350, p.ratingDeviation(), 0.0001);
        assertEquals(0.06, p.volatility(), 0.0001);
        assertEquals("#0", p.tier());
        assertFalse(p.ranked());
    }

    @Test
    void inPlacementsUntilRequiredReached() {
        RankedProfile p = profile();
        assertTrue(p.inPlacements(7));
        p.setPlacementPlayed(6);
        assertTrue(p.inPlacements(7));
        p.setPlacementPlayed(7);
        assertFalse(p.inPlacements(7));
    }

    @Test
    void rankedPlayerIsNeverInPlacements() {
        RankedProfile p = profile();
        p.setRanked(true);
        p.setPlacementPlayed(0);
        assertFalse(p.inPlacements(7));
    }

    @Test
    void streakLabelReflectsState() {
        RankedProfile p = profile();
        assertEquals("No Streak", p.streakLabel());
        p.setWinStreak(5);
        assertEquals("5 Win Streak", p.streakLabel());
        p.setWinStreak(0);
        p.setLossStreak(3);
        assertEquals("3 Loss Streak", p.streakLabel());
    }

    @Test
    void placementNetWinsIsWinsMinusLosses() {
        RankedProfile p = profile();
        p.setPlacementWins(5);
        p.setPlacementLosses(2);
        assertEquals(3, p.placementNetWins());
    }

    @Test
    void averageOpponentRatingDefaultsTo1500WithNoGames() {
        RankedProfile p = profile();
        assertEquals(1500, p.averagePlacementOpponentRating(), 0.0001);
    }

    @Test
    void averageOpponentRatingComputesMean() {
        RankedProfile p = profile();
        p.addPlacementOpponentRating(1400);
        p.addPlacementOpponentRating(1600);
        p.setPlacementPlayed(2);
        assertEquals(1500, p.averagePlacementOpponentRating(), 0.0001);
    }
}
