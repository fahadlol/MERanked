package com.meranked.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RankedMatchTest {

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    private RankedMatch match() {
        return new RankedMatch("M1", "Mace", p1, p2);
    }

    @Test
    void opponentResolvesBothDirections() {
        RankedMatch m = match();
        assertEquals(p2, m.opponent(p1));
        assertEquals(p1, m.opponent(p2));
    }

    @Test
    void newMatchDefaults() {
        RankedMatch m = match();
        assertEquals(RankedMatch.State.VOTING, m.state());
        assertEquals(1, m.currentRound());
        assertFalse(m.hasStarted());
        assertFalse(m.bestOfThree());
        assertEquals(0, m.roundWins(p1));
        assertEquals(0, m.roundWins(p2));
    }

    @Test
    void markFinalizedIsIdempotent() {
        RankedMatch m = match();
        assertTrue(m.markFinalized(), "first finalize should succeed");
        assertFalse(m.markFinalized(), "second finalize must be rejected");
        assertFalse(m.markFinalized(), "further finalize attempts must be rejected");
    }

    @Test
    void roundWinsAccumulate() {
        RankedMatch m = match();
        m.addRoundWin(p1);
        m.addRoundWin(p1);
        m.addRoundWin(p2);
        assertEquals(2, m.roundWins(p1));
        assertEquals(1, m.roundWins(p2));
    }

    @Test
    void hasStartedFlagTracksLifecycle() {
        RankedMatch m = match();
        assertFalse(m.hasStarted());
        m.setHasStarted(true);
        assertTrue(m.hasStarted());
    }

    @Test
    void statsAreTrackedPerPlayer() {
        RankedMatch m = match();
        m.stats(p1).addDamage(12.5);
        m.stats(p1).incrementTotems();
        assertEquals(12.5, m.stats(p1).damageDealt(), 0.0001);
        assertEquals(1, m.stats(p1).totemsPopped());
        assertEquals(0, m.stats(p2).totemsPopped());
    }
}
