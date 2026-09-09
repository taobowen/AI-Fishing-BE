package com.aifishing.planning.repo;

import com.aifishing.planning.domain.TripWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripWaypointRepository extends JpaRepository<TripWaypoint, UUID> {

    List<TripWaypoint> findByTripPlanIdOrderBySequenceAsc(UUID tripPlanId);
}
