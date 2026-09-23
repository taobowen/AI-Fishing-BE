package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.GuidanceClientHints;

/**
 * Non-authoritative user input for {@code FishingAgentFacade.run}.
 * Reuses the note/hints from {@code GuidanceDecisionRequest}; hints never override
 * the server snapshot.
 */
public record OptionalUserInput(
        String userNote,
        GuidanceClientHints clientHints
) {
}
