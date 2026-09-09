package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingTargetSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingTargetSampleRepository extends JpaRepository<LakeFishingTargetSample, UUID> {

    List<LakeFishingTargetSample> findBySpatialPlanningSnapshotId(UUID snapshotId);

    List<LakeFishingTargetSample> findByFishingTargetIdOrderByFractionAsc(UUID fishingTargetId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
