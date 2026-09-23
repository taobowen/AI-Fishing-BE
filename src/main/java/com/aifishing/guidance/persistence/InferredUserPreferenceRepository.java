package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InferredUserPreferenceRepository extends JpaRepository<InferredUserPreferenceEntity, UUID> {

    Optional<InferredUserPreferenceEntity> findByUserIdAndKey(UUID userId, String key);

    List<InferredUserPreferenceEntity> findByUserId(UUID userId);
}
