package com.aifishing.planning.repo;

import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripPlanningInputSnapshotRepository extends JpaRepository<TripPlanningInputSnapshot, UUID> {

    Optional<TripPlanningInputSnapshot> findByPlanningRunId(UUID planningRunId);
}
