package com.meranked.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RankedProfilePersistenceFieldsTest {

    @Test
    void placementAndStreakFieldsRoundTripInMemory() {
        RankedProfile p = new RankedProfile(java.util.UUID.randomUUID(), "Mace");
        p.setPlacementCapOverride("LT4");
        p.setPlacementBehaviorBias(12.5);
        p.setLossStreak(3);
        p.setPlacementOpponentRatingSum(4500);
        p.setWorstPlacementLossRating(1600);
        p.setUpsetWins(2);
        p.setHighestBeatenRating(1750);

        assertEquals("LT4", p.placementCapOverride());
        assertEquals(12.5, p.placementBehaviorBias());
        assertEquals(3, p.lossStreak());
        assertEquals(4500, p.placementOpponentRatingSum());
        assertEquals(1600, p.worstPlacementLossRating());
        assertEquals(2, p.upsetWins());
        assertEquals(1750, p.highestBeatenRating());
        assertEquals("3 Loss Streak", p.streakLabel());
    }
}
