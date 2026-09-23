package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuidancePlanVersionRepository extends JpaRepository<GuidancePlanVersionEntity, UUID> {

    Optional<GuidancePlanVersionEntity> findFirstByFishingSessionIdOrderByVersionDesc(UUID fishingSessionId);

    Optional<GuidancePlanVersionEntity> findByFishingSessionIdAndVersion(UUID fishingSessionId, int version);

    List<GuidancePlanVersionEntity> findByFishingSessionIdOrderByVersionAsc(UUID fishingSessionId);
}
