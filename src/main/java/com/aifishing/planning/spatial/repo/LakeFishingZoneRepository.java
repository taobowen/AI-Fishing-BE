package com.aifishing.planning.spatial.repo;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingZoneRepository extends JpaRepository<LakeFishingZone, UUID> {

    List<LakeFishingZone> findByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndBuilderVersion(
            UUID lakeId,
            Pipeline featurePipeline,
            String featureAnalysisVersion,
            String builderVersion
    );

    List<LakeFishingZone> findBySpatialPlanningSnapshotId(UUID snapshotId);

    void deleteBySpatialPlanningSnapshotId(UUID snapshotId);
}
