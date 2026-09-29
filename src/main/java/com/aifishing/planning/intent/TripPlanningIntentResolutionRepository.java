package com.aifishing.planning.intent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripPlanningIntentResolutionRepository extends JpaRepository<TripPlanningIntentResolution, UUID> {

    List<TripPlanningIntentResolution> findByPlanningInputTargetIdOrderByRankAsc(UUID planningInputTargetId);
}
