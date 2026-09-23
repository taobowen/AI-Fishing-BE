package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserActionEventRepository extends JpaRepository<UserActionEventEntity, UUID> {

    List<UserActionEventEntity> findByFishingSessionIdOrderByOccurredAtAsc(UUID fishingSessionId);

    List<UserActionEventEntity> findByDeliveredDecisionIdOrderByOccurredAtAsc(UUID deliveredDecisionId);

    List<UserActionEventEntity> findByFishingSessionIdInOrderByOccurredAtAsc(Collection<UUID> fishingSessionIds);

    List<UserActionEventEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            Instant windowStart,
            Instant windowEnd
    );
}
