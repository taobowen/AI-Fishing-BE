package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCallEntity, UUID> {

    List<AgentToolCallEntity> findByRunIdOrderByObservedAtAsc(UUID runId);
}
