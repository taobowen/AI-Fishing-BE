package com.aifishing.fishingsession.repo;

import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionWaypointProgressRepository extends JpaRepository<SessionWaypointProgress, UUID> {

    List<SessionWaypointProgress> findByFishingSessionIdOrderBySequenceAsc(UUID fishingSessionId);

    Optional<SessionWaypointProgress> findByFishingSessionIdAndTripWaypointId(UUID fishingSessionId, UUID tripWaypointId);
}
