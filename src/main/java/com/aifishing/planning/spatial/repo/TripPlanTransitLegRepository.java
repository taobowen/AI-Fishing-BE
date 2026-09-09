package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripPlanTransitLegRepository extends JpaRepository<TripPlanTransitLeg, UUID> {

    List<TripPlanTransitLeg> findByTripPlanIdOrderBySequenceAsc(UUID tripPlanId);

    long countByTripPlanId(UUID tripPlanId);
}
