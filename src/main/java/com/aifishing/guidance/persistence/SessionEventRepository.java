package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.SessionEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionEventRepository extends JpaRepository<SessionEventEntity, UUID> {

    List<SessionEventEntity> findByFishingSessionIdOrderByOccurredAtAsc(UUID fishingSessionId);

    Optional<SessionEventEntity> findByFishingSessionIdAndIdempotencyKey(UUID fishingSessionId, String idempotencyKey);

    boolean existsByFishingSessionIdAndTypeAndFishInteractionId(
            UUID fishingSessionId,
            SessionEventType type,
            UUID fishInteractionId
    );

    List<SessionEventEntity> findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
            UUID fishingSessionId,
            Collection<SessionEventType> types
    );

    List<SessionEventEntity> findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
            UUID fishingSessionId,
            SessionEventType type,
            Instant occurredAt
    );
}
