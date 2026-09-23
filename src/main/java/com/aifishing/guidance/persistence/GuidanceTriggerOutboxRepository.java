package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.GuidanceTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuidanceTriggerOutboxRepository extends JpaRepository<GuidanceTriggerOutboxEntity, UUID> {

    Optional<GuidanceTriggerOutboxEntity> findFirstByFishingSessionIdAndStatusIn(
            UUID fishingSessionId,
            Collection<GuidanceTriggerOutboxStatus> statuses
    );

    List<GuidanceTriggerOutboxEntity> findByFishingSessionIdOrderByCreatedAtAsc(UUID fishingSessionId);

    List<GuidanceTriggerOutboxEntity> findByFishingSessionIdAndCreatedAtGreaterThanEqual(
            UUID fishingSessionId,
            Instant createdAt
    );

    List<GuidanceTriggerOutboxEntity> findByFishingSessionIdAndPrimaryTriggerAndCreatedAtGreaterThanEqual(
            UUID fishingSessionId,
            GuidanceTrigger primaryTrigger,
            Instant createdAt
    );
}
