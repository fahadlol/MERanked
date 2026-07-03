package com.meranked.rating;

import com.meranked.config.ConfigService;
import com.meranked.model.RankedProfile;

/**
 * Glicko-2 style rating engine isolated for future formula changes.
 */
public final class RatingService {

    private static final double TAU = 0.5;
    private static final double EPSILON = 0.000001;

    private final ConfigService configService;
    private final TierService tierService;

    public RatingService(ConfigService configService, TierService tierService) {
        this.configService = configService;
        this.tierService = tierService;
    }

    public RatingUpdate calculate(RankedProfile player, RankedProfile opponent, double score) {
        double mu = toMu(player.rating());
        double phi = toPhi(player.ratingDeviation());
        double sigma = player.volatility();

        double muOpp = toMu(opponent.rating());
        double phiOpp = toPhi(opponent.ratingDeviation());

        double g = g(phiOpp);
        double e = expectedScore(mu, muOpp, phiOpp);

        double v = 1.0 / (g * g * e * (1 - e));
        double delta = v * g * (score - e);

        double a = Math.log(sigma * sigma);
        double newSigma = solveSigma(delta, phi, v, a, sigma);

        double phiStar = Math.sqrt(phi * phi + newSigma * newSigma);
        double newPhi = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double newMu = mu + newPhi * newPhi * g * (score - e);

        double newRating = fromMu(newMu);
        double newRd = fromPhi(newPhi);

        return new RatingUpdate(newRating, newRd, newSigma, newRating - player.rating());
    }

    public PotentialChange calculatePotential(RankedProfile player, RankedProfile opponent) {
        RatingUpdate win = calculate(player, opponent, 1.0);
        RatingUpdate loss = calculate(player, opponent, 0.0);
        return new PotentialChange(Math.max(0, Math.round(win.change())), Math.max(0, Math.round(Math.abs(loss.change()))));
    }

    public void applyDecay(RankedProfile profile, double rdIncrease) {
        profile.setRatingDeviation(Math.min(350, profile.ratingDeviation() + rdIncrease));
        profile.setDecayActive(true);
    }

    private double solveSigma(double delta, double phi, double v, double a, double sigma) {
        double A = a;
        double B;
        if (delta * delta > phi * phi + v) {
            B = Math.log(delta * delta - phi * phi - v);
        } else {
            int k = 1;
            while (f(a - k * TAU, delta, phi, v, a) < 0) k++;
            B = a - k * TAU;
        }

        double fA = f(A, delta, phi, v, a);
        double fB = f(B, delta, phi, v, a);

        while (Math.abs(B - A) > EPSILON) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = f(C, delta, phi, v, a);
            if (fC * fB <= 0) {
                A = B;
                fA = fB;
            } else {
                fA /= 2;
            }
            B = C;
            fB = fC;
        }
        return Math.exp(A / 2);
    }

    private double f(double x, double delta, double phi, double v, double a) {
        double ex = Math.exp(x);
        double num = ex * (delta * delta - phi * phi - v - ex);
        double den = 2 * Math.pow(phi * phi + v + ex, 2);
        return num / den - (x - a) / (TAU * TAU);
    }

    private double g(double phi) {
        return 1.0 / Math.sqrt(1 + 3 * phi * phi / (Math.PI * Math.PI));
    }

    private double expectedScore(double mu, double muOpp, double phiOpp) {
        return 1.0 / (1 + Math.exp(-g(phiOpp) * (mu - muOpp)));
    }

    private double toMu(double rating) {
        return (rating - 1500) / 173.7178;
    }

    private double fromMu(double mu) {
        return 173.7178 * mu + 1500;
    }

    private double toPhi(double rd) {
        return rd / 173.7178;
    }

    private double fromPhi(double phi) {
        return 173.7178 * phi;
    }

    public record RatingUpdate(double rating, double rd, double volatility, double change) {}
    public record PotentialChange(long winGain, long lossLoss) {}
}
