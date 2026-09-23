package com.aifishing.guidance.empirical;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface HistoricalContributionStore {

    /**
     * Locks the session's contribution rows, replaces them, and returns the union
     * of previous and new grains for that session.
     */
    Set<EmpiricalGrain> replaceSession(
            UUID fishingSessionId,
            Collection<SessionContribution> contributions,
            Instant rebuiltAt,
            String sourceHash
    );

    EmpiricalRawCounts sumMatching(EmpiricalMatch match);
}
