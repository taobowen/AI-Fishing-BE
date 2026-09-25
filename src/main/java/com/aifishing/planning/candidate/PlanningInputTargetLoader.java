package com.aifishing.planning.candidate;

import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;
import com.aifishing.planning.repo.TripPlanningInputTargetRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loads frozen planning-input rows for a generate run. */
@Component
public class PlanningInputTargetLoader {

    private final TripPlanningInputSnapshotRepository snapshotRepository;
    private final TripPlanningInputTargetRepository targetRepository;

    public PlanningInputTargetLoader(
            TripPlanningInputSnapshotRepository snapshotRepository,
            TripPlanningInputTargetRepository targetRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.targetRepository = targetRepository;
    }

    public Optional<TripPlanningInputSnapshot> findSnapshot(UUID planningRunId) {
        if (planningRunId == null) {
            return Optional.empty();
        }
        return snapshotRepository.findByPlanningRunId(planningRunId);
    }

    public List<TripPlanningInputTarget> findTargets(UUID snapshotId) {
        if (snapshotId == null) {
            return List.of();
        }
        return targetRepository.findBySnapshotIdOrderBySortOrderAscIdAsc(snapshotId);
    }
}
