package com.aifishing.feedback.performance;

import com.aifishing.feedback.FeedbackProperties;

public final class ShrinkageScorer {

    private ShrinkageScorer() {
    }

    public record Result(
            double posteriorRate,
            double rawHistoricalScore,
            double evidenceConfidence,
            double historicalPerformance
    ) {
    }

    public static Result score(double landed, double effortHours, FeedbackProperties.Performance cfg) {
        if (effortHours < cfg.minEffortHours()) {
            return new Result(cfg.getPriorLandedPerHour(), 0.5, 0, 0.5);
        }
        double priorHours = cfg.getPriorEffortHours();
        double priorRate = cfg.getPriorLandedPerHour();
        if (priorHours <= 0 || priorRate <= 0) {
            return new Result(priorRate, 0.5, 0, 0.5);
        }
        double posteriorRate = (landed + priorRate * priorHours) / (effortHours + priorHours);
        double raw = clamp(0.5 + 0.5 * Math.tanh((posteriorRate - priorRate) / priorRate));
        double evidenceConfidence = effortHours / (effortHours + priorHours);
        double historical = 0.5 + evidenceConfidence * (raw - 0.5);
        return new Result(posteriorRate, raw, evidenceConfidence, clamp(historical));
    }

    public static Result blend(Result personal, Result global, double userEffortHours, double userBlendPriorHours) {
        double blend = userEffortHours / (userEffortHours + Math.max(0.0001, userBlendPriorHours));
        return new Result(
                blend * personal.posteriorRate() + (1 - blend) * global.posteriorRate(),
                clamp(blend * personal.rawHistoricalScore() + (1 - blend) * global.rawHistoricalScore()),
                clamp(blend * personal.evidenceConfidence() + (1 - blend) * global.evidenceConfidence()),
                clamp(blend * personal.historicalPerformance() + (1 - blend) * global.historicalPerformance())
        );
    }

    public static Double landedCpue(int landed, double fishingHours) {
        if (fishingHours <= 0) {
            return null;
        }
        return landed / fishingHours;
    }

    public static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, value));
    }
}
