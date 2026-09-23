package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SemanticMemoryRepository extends JpaRepository<SemanticMemoryEntity, UUID> {

    List<SemanticMemoryEntity> findTop8ByUserIdOrderByCreatedAtDesc(UUID userId);
}
