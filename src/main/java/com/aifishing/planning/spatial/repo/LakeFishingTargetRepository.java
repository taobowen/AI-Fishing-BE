package com.aifishing.planning.spatial.repo;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = """
            select * from lake_fishing_targets
             where ST_DWithin(
                representative_point::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusMeters
             )
             limit 50
            """, nativeQuery = true)
    List<LakeFishingTarget> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters
    );
}
