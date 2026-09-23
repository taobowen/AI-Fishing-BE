package com.aifishing.planning.repo;

import com.aifishing.planning.domain.TripWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripWaypointRepository extends JpaRepository<TripWaypoint, UUID> {

    List<TripWaypoint> findByTripPlanIdOrderBySequenceAsc(UUID tripPlanId);

    @Query("""
            select w.tripPlanId, count(w)
            from TripWaypoint w
            where w.tripPlanId in :planIds
            group by w.tripPlanId
            """)
    List<Object[]> countByTripPlanIdIn(@Param("planIds") Collection<UUID> planIds);

    @Query(value = """
            select * from trip_waypoints
             where ST_DWithin(
                location::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusMeters
             )
             limit 50
            """, nativeQuery = true)
    List<TripWaypoint> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters
    );
}
