package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuidanceEvalCaseResultRepository extends JpaRepository<GuidanceEvalCaseResultEntity, UUID> {

    List<GuidanceEvalCaseResultEntity> findByEvalRunIdOrderByCreatedAtAsc(UUID evalRunId);
}
