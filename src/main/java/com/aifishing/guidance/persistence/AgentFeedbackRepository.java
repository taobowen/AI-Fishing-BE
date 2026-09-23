package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentFeedbackRepository extends JpaRepository<AgentFeedbackEntity, UUID> {

    List<AgentFeedbackEntity> findByFishingSessionIdOrderByOccurredAtAsc(UUID fishingSessionId);

    List<AgentFeedbackEntity> findByDeliveredDecisionIdOrderByOccurredAtAsc(UUID deliveredDecisionId);

    List<AgentFeedbackEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            Instant windowStart,
            Instant windowEnd
    );
}
