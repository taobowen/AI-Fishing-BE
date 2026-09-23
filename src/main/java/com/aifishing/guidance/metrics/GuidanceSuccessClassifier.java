package com.aifishing.guidance.metrics;

import com.aifishing.guidance.attribution.AttributionWindows;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.OutcomeKind;

import java.util.UUID;

/**
 * Strict guidance success is followedRecommendation + FISH_ON-family only.
 * BITE_SIGNAL_ONLY stays distinct from NO_FISH_SIGNAL. Landing is not a member.
 */
public final class GuidanceSuccessClassifier {

    private GuidanceSuccessClassifier() {
    }

    public static GuidanceSuccessKind classify(
            UUID deliveredDecisionId,
            boolean followedRecommendation,
            OutcomeKind outcomeKind
    ) {
        if (deliveredDecisionId == null) {
            return GuidanceSuccessKind.UNATTRIBUTED;
        }
        if (!followedRecommendation) {
            return GuidanceSuccessKind.NOT_FOLLOWED;
        }
        if (AttributionWindows.strategySuccess(outcomeKind)) {
            return GuidanceSuccessKind.FISH_ON_SUCCESS;
        }
        if (outcomeKind == OutcomeKind.BITE) {
            return GuidanceSuccessKind.BITE_SIGNAL_ONLY;
        }
        return GuidanceSuccessKind.NO_FISH_SIGNAL;
    }

    public static boolean landing(OutcomeKind outcomeKind) {
        return outcomeKind == OutcomeKind.CATCH_LANDED;
    }

    public static boolean lostAfterHook(OutcomeKind outcomeKind) {
        return outcomeKind == OutcomeKind.CATCH_LOST;
    }
}
