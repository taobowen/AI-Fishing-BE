package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingWaterPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeFishingWaterPathRepository extends JpaRepository<LakeFishingWaterPath, UUID> {

    List<LakeFishingWaterPath> findBySpatialPlanningSnapshotId(UUID snapshotId);

    @Query("""
            select p.zoneId as zoneId, p.fromKey as fromKey, p.toKey as toKey
            from LakeFishingWaterPath p
            where p.spatialPlanningSnapshotId = :snapshotId and p.zoneId is not null
            """)
    List<ZonePathKey> findZonePathKeys(@Param("snapshotId") UUID snapshotId);

    interface ZonePathKey {
        UUID getZoneId();
        String getFromKey();
        String getToKey();
    }

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
