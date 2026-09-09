package com.aifishing.planning.spatial.repo;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingTargetRepository extends JpaRepository<LakeFishingTarget, UUID> {

    List<LakeFishingTarget> findByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndDerivationVersion(
            UUID lakeId,
            Pipeline featurePipeline,
            String featureAnalysisVersion,
            String derivationVersion
    );

    List<LakeFishingTarget> findBySpatialPlanningSnapshotId(UUID snapshotId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
