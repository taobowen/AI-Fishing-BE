package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingZonePortal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingZonePortalRepository extends JpaRepository<LakeFishingZonePortal, UUID> {

    List<LakeFishingZonePortal> findBySpatialPlanningSnapshotIdOrderByZoneIdAscSequenceAsc(UUID snapshotId);

    List<LakeFishingZonePortal> findByZoneIdOrderBySequenceAsc(UUID zoneId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
