package com.aifishing.guidance.contracts;

public record GuidanceDecisionRequest(
        GuidanceTrigger trigger,
        String userNote,
        GuidanceClientHints clientHints
) {
}
