package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeFishingWaterPathRepository extends JpaRepository<LakeFishingWaterPath, UUID> {

    List<LakeFishingWaterPath> findBySpatialPlanningSnapshotId(UUID snapshotId);

    Optional<LakeFishingWaterPath> findBySpatialPlanningSnapshotIdAndZoneIdAndFromKeyAndToKey(
            UUID snapshotId,
            UUID zoneId,
            String fromKey,
            String toKey
    );

    Optional<LakeFishingWaterPath> findFirstBySpatialPlanningSnapshotIdAndFromKeyAndToKeyAndZoneIdIsNull(
            UUID snapshotId,
            String fromKey,
            String toKey
    );

    long countBySpatialPlanningSnapshotIdAndPrecomputedTrue(UUID snapshotId);

    long countBySpatialPlanningSnapshotIdAndPrecomputedFalse(UUID snapshotId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
