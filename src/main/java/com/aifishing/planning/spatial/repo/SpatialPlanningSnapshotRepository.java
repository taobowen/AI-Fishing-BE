package com.aifishing.planning.spatial.repo;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpatialPlanningSnapshotRepository extends JpaRepository<SpatialPlanningSnapshot, UUID> {

    Optional<SpatialPlanningSnapshot> findByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersion(
            UUID lakeId,
            Pipeline featurePipeline,
            String featureAnalysisVersion,
            String targetDerivationVersion,
            String zoneBuilderVersion,
            String navigationVersion
    );

    Optional<SpatialPlanningSnapshot> findFirstByLakeIdAndFeaturePipelineAndFeatureAnalysisVersionAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersionAndStatus(
            UUID lakeId,
            Pipeline featurePipeline,
            String featureAnalysisVersion,
            String targetDerivationVersion,
            String zoneBuilderVersion,
            String navigationVersion,
            SpatialSnapshotStatus status
    );

    List<SpatialPlanningSnapshot> findByLakeIdOrderByCreatedAtDesc(UUID lakeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SpatialPlanningSnapshot s where s.id = :id")
    Optional<SpatialPlanningSnapshot> lockById(@Param("id") UUID id);
}
