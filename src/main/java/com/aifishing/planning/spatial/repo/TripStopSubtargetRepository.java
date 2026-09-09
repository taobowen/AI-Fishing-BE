package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripStopSubtargetRepository extends JpaRepository<TripStopSubtarget, UUID> {

    List<TripStopSubtarget> findByTripWaypointIdOrderBySequenceAsc(UUID tripWaypointId);

    List<TripStopSubtarget> findByTripWaypointIdInOrderByTripWaypointIdAscSequenceAsc(Collection<UUID> tripWaypointIds);
}
