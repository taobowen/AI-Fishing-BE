package com.aifishing.planning.repo;

import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.planning.domain.TripPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripPlanRepository extends JpaRepository<TripPlan, UUID> {

    List<TripPlan> findByTripIdOrderByVersionDesc(UUID tripId);

    List<TripPlan> findByTripIdAndStatus(UUID tripId, TripPlanStatus status);

    Optional<TripPlan> findFirstByTripIdAndStatusInOrderByVersionDesc(UUID tripId, Collection<TripPlanStatus> statuses);

    List<TripPlan> findByTripIdInAndStatusIn(Collection<UUID> tripIds, Collection<TripPlanStatus> statuses);

    @Query("select coalesce(max(p.version), 0) from TripPlan p where p.tripId = :tripId")
    int maxVersion(@Param("tripId") UUID tripId);

    Optional<TripPlan> findByIdAndTripId(UUID id, UUID tripId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE trip_plans
            SET tactics_status = 'GENERATING', tactics_started_at = :now
            WHERE id = :id
              AND (
                tactics_status IN ('NONE', 'FAILED')
                OR (tactics_status = 'GENERATING' AND tactics_started_at < :staleBefore)
              )
            """, nativeQuery = true)
    int claimTacticsGeneration(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore
    );
}
