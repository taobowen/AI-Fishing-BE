package com.aifishing.planning.repo;

import com.aifishing.planning.domain.TripPlanningInputTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripPlanningInputTargetRepository extends JpaRepository<TripPlanningInputTarget, UUID> {

    List<TripPlanningInputTarget> findBySnapshotIdOrderBySortOrderAscIdAsc(UUID snapshotId);
}
