package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AgentRunVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, UUID> {

    /**
     * Unfiltered latest run. Production callers must use
     * {@link #findFirstByFishingSessionIdAndVisibilityOrderByStartedAtDesc(UUID, AgentRunVisibility)}
     * with {@link AgentRunVisibility#PRODUCTION}.
     */
    Optional<AgentRunEntity> findFirstByFishingSessionIdOrderByStartedAtDesc(UUID fishingSessionId);

    Optional<AgentRunEntity> findFirstByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
            UUID fishingSessionId,
            AgentRunVisibility visibility
    );

    List<AgentRunEntity> findByFishingSessionIdOrderByStartedAtDesc(UUID fishingSessionId);

    List<AgentRunEntity> findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
            UUID fishingSessionId,
            AgentRunVisibility visibility
    );

    List<AgentRunEntity> findByFishingSessionIdOrderByStartedAtAsc(UUID fishingSessionId);
}
