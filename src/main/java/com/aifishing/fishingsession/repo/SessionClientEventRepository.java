package com.aifishing.fishingsession.repo;

import com.aifishing.fishingsession.domain.SessionClientEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionClientEventRepository extends JpaRepository<SessionClientEvent, UUID> {

    Optional<SessionClientEvent> findByFishingSessionIdAndClientEventId(UUID fishingSessionId, String clientEventId);

    long countByFishingSessionId(UUID fishingSessionId);
}
