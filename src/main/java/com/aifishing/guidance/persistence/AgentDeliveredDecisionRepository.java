package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDeliveredDecisionRepository extends JpaRepository<AgentDeliveredDecisionEntity, UUID> {

    Optional<AgentDeliveredDecisionEntity> findFirstByRunId(UUID runId);

    List<AgentDeliveredDecisionEntity> findByRunIdInOrderByCreatedAtAsc(List<UUID> runIds);

    @Query("""
            select d from AgentDeliveredDecisionEntity d, AgentRunEntity r
            where d.runId = r.id
              and r.fishingSessionId = :sessionId
              and r.visibility = com.aifishing.guidance.contracts.AgentRunVisibility.PRODUCTION
            order by d.createdAt asc
            """)
    List<AgentDeliveredDecisionEntity> findByFishingSessionIdOrderByCreatedAtAsc(@Param("sessionId") UUID sessionId);
}
