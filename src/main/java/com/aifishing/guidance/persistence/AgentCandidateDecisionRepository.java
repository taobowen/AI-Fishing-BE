package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentCandidateDecisionRepository extends JpaRepository<AgentCandidateDecisionEntity, UUID> {

    Optional<AgentCandidateDecisionEntity> findFirstByRunId(UUID runId);
}
