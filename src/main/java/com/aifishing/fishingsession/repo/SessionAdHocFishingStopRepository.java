package com.aifishing.fishingsession.repo;

import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAdHocFishingStopRepository extends JpaRepository<SessionAdHocFishingStop, UUID> {

    Optional<SessionAdHocFishingStop> findFirstByFishingSessionIdAndEndedAtIsNull(UUID fishingSessionId);

    Optional<SessionAdHocFishingStop> findFirstByFishingSessionIdAndEndedAtIsNotNullOrderByEndedAtDesc(
            UUID fishingSessionId
    );

    Optional<SessionAdHocFishingStop> findByFishingSessionIdAndClientEventId(
            UUID fishingSessionId,
            String clientEventId
    );

    List<SessionAdHocFishingStop> findByFishingSessionIdOrderByStartedAtAsc(UUID fishingSessionId);
}
