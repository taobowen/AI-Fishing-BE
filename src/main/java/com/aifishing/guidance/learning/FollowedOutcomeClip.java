package com.aifishing.guidance.learning;

import java.util.UUID;

public record FollowedOutcomeClip(
        UUID attributionId,
        String dimension,
        String outcomeKind,
        boolean followedRecommendation
) {
    public String memoryRefId() {
        return "attribution:" + attributionId;
    }

    public String clip() {
        String dim = dimension == null ? "unknown" : dimension.toLowerCase();
        if ("FISH_ON".equals(outcomeKind) || "CATCH_LANDED".equals(outcomeKind) || "CATCH_LOST".equals(outcomeKind)) {
            return "Followed " + dim + " advice produced fish-on (" + outcomeKind + ")";
        }
        if ("NO_BITE".equals(outcomeKind)) {
            return "Followed " + dim + " advice had no bite";
        }
        return "Followed " + dim + " advice outcome " + outcomeKind;
    }
}
