package com.aifishing.fishingsession.repo;

import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SessionLocationPointRepository extends JpaRepository<SessionLocationPoint, UUID> {

    List<SessionLocationPoint> findByFishingSessionIdOrderByRecordedAtAsc(UUID fishingSessionId);

    List<SessionLocationPoint> findByFishingSessionIdAndQualityOrderByRecordedAtAsc(
            UUID fishingSessionId,
            LocationQuality quality
    );

    Optional<SessionLocationPoint> findFirstByFishingSessionIdOrderByRecordedAtDesc(UUID fishingSessionId);

    Optional<SessionLocationPoint> findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(
            UUID fishingSessionId,
            LocationQuality quality
    );

    @Query("""
            select p.clientPointId
            from SessionLocationPoint p
            where p.fishingSessionId = :sessionId
              and p.clientPointId in :ids
            """)
    Set<String> findExistingClientPointIds(
            @Param("sessionId") UUID sessionId,
            @Param("ids") Collection<String> ids
    );

    long countByFishingSessionId(UUID fishingSessionId);

    long countByFishingSessionIdAndQuality(UUID fishingSessionId, LocationQuality quality);
}
