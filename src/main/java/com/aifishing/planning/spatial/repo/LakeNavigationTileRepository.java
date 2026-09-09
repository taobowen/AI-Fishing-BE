package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeNavigationTile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeNavigationTileRepository extends JpaRepository<LakeNavigationTile, UUID> {

    List<LakeNavigationTile> findBySpatialPlanningSnapshotId(UUID snapshotId);

    long countBySpatialPlanningSnapshotId(UUID snapshotId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
