package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GuidanceLearningOutboxRepository extends JpaRepository<GuidanceLearningOutboxEntity, UUID> {

    Optional<GuidanceLearningOutboxEntity> findByIdempotencyKey(String idempotencyKey);
}
