package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingNavNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingNavNodeRepository extends JpaRepository<LakeFishingNavNode, UUID> {

    List<LakeFishingNavNode> findBySpatialPlanningSnapshotId(UUID snapshotId);

    List<LakeFishingNavNode> findByZoneId(UUID zoneId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
