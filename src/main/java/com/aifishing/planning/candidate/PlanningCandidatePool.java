package com.aifishing.planning.candidate;

import com.aifishing.common.enums.PlanningMode;
import com.aifishing.planning.service.PlanningContext;

import java.util.List;
import java.util.UUID;

/**
 * Builds the mode-specific candidate pool after the spatial snapshot is loaded
 * and before filters. Required Points are returned separately as hard constraints.
 */
public interface PlanningCandidatePool {

    Result build(
            PlanningMode mode,
            List<CandidateSpot> aiSpots,
            PlanningContext context,
            UUID planningRunId
    );

    record Result(
            List<CandidateSpot> candidates,
            List<CandidateSpot> requiredConstraints,
            List<String> warnings
    ) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            requiredConstraints = requiredConstraints == null ? List.of() : List.copyOf(requiredConstraints);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
