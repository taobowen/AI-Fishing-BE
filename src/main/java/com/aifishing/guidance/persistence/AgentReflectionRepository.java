package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentReflectionRepository extends JpaRepository<AgentReflectionEntity, UUID> {

    List<AgentReflectionEntity> findByFishingSessionIdOrderByCreatedAtAsc(UUID fishingSessionId);
}
