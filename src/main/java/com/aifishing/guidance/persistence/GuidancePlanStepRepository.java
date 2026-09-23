package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuidancePlanStepRepository extends JpaRepository<GuidancePlanStepEntity, UUID> {

    List<GuidancePlanStepEntity> findByGuidancePlanVersionIdOrderByStepAsc(UUID guidancePlanVersionId);
}
