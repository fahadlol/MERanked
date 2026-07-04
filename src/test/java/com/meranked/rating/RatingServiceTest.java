package com.meranked.rating;

import com.meranked.model.RankedProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Glicko-2 rating engine. The engine's math is independent of config/tier services,
 * so it can be exercised in isolation.
 */
class RatingServiceTest {

    private final RatingService rating = new RatingService(null, null);

    private RankedProfile profile(double r, double rd, double vol) {
        RankedProfile p = new RankedProfile(UUID.randomUUID(), "Mace");
        p.setRating(r);
        p.setRatingDeviation(rd);
        p.setVolatility(vol);
        return p;
    }

    @Test
    void winnerGainsAndLoserLoses() {
        RankedProfile a = profile(1500, 350, 0.06);
        RankedProfile b = profile(1500, 350, 0.06);

        RatingService.RatingUpdate win = rating.calculate(a, b, 1.0);
        RatingService.RatingUpdate loss = rating.calculate(b, a, 0.0);

        assertTrue(win.rating() > 1500, "winner should gain rating");
        assertTrue(loss.rating() < 1500, "loser should lose rating");
        assertTrue(win.change() > 0);
        assertTrue(loss.change() < 0);
    }

    @Test
    void equalPlayersProduceSymmetricSwing() {
        RankedProfile a = profile(1500, 350, 0.06);
        RankedProfile b = profile(1500, 350, 0.06);

        double gain = rating.calculate(a, b, 1.0).change();
        double drop = rating.calculate(b, a, 0.0).change();

        assertEquals(gain, -drop, 1.0, "equal players should gain/lose symmetrically");
    }

    @Test
    void ratingDeviationShrinksAfterAGame() {
        RankedProfile a = profile(1500, 350, 0.06);
        RankedProfile b = profile(1500, 350, 0.06);

        RatingService.RatingUpdate update = rating.calculate(a, b, 1.0);

        assertTrue(update.rd() < 350, "RD should decrease after playing");
        assertTrue(update.rd() > 0, "RD must stay positive");
        assertTrue(update.volatility() > 0, "volatility must stay positive");
    }

    @Test
    void beatingStrongerOpponentGivesMoreThanBeatingEqual() {
        RankedProfile me = profile(1500, 200, 0.06);
        RankedProfile equal = profile(1500, 200, 0.06);
        RankedProfile stronger = profile(1900, 200, 0.06);

        double vsEqual = rating.calculate(me, equal, 1.0).change();
        double vsStronger = rating.calculate(me, stronger, 1.0).change();

        assertTrue(vsStronger > vsEqual, "upset win should yield more rating");
    }

    @Test
    void losingToWeakerOpponentCostsMoreThanLosingToEqual() {
        RankedProfile me = profile(1500, 200, 0.06);
        RankedProfile equal = profile(1500, 200, 0.06);
        RankedProfile weaker = profile(1100, 200, 0.06);

        double vsEqual = rating.calculate(me, equal, 0.0).change();
        double vsWeaker = rating.calculate(me, weaker, 0.0).change();

        assertTrue(vsWeaker < vsEqual, "losing to a weaker player should cost more");
    }

    @Test
    void potentialChangeIsNeverNegative() {
        RankedProfile a = profile(1500, 120, 0.06);
        RankedProfile b = profile(1650, 120, 0.06);

        RatingService.PotentialChange pot = rating.calculatePotential(a, b);

        assertTrue(pot.winGain() >= 0);
        assertTrue(pot.lossLoss() >= 0);
    }

    @Test
    void decayIncreasesDeviationAndFlags() {
        RankedProfile p = profile(1500, 80, 0.06);
        rating.applyDecay(p, 40);
        assertEquals(120, p.ratingDeviation(), 0.0001);
        assertTrue(p.decayActive());
    }

    @Test
    void decayIsCappedAt350() {
        RankedProfile p = profile(1500, 340, 0.06);
        rating.applyDecay(p, 100);
        assertEquals(350, p.ratingDeviation(), 0.0001);
    }
}
