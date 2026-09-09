package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingNavEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingNavEdgeRepository extends JpaRepository<LakeFishingNavEdge, UUID> {

    List<LakeFishingNavEdge> findBySpatialPlanningSnapshotId(UUID snapshotId);

    List<LakeFishingNavEdge> findByZoneId(UUID zoneId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);

    long countBySpatialPlanningSnapshotId(UUID snapshotId);
}
