package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentValidationRepository extends JpaRepository<AgentValidationEntity, UUID> {

    Optional<AgentValidationEntity> findFirstByRunId(UUID runId);
}
