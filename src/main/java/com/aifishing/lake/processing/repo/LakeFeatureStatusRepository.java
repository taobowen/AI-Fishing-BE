package com.aifishing.lake.processing.repo;

import com.aifishing.lake.processing.domain.LakeFeatureStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeFeatureStatusRepository extends JpaRepository<LakeFeatureStatus, UUID> {

    List<LakeFeatureStatus> findByLakeIdOrderByFeatureTypeAsc(UUID lakeId);

    List<LakeFeatureStatus> findByLakeIdAndPipelineOrderByFeatureTypeAsc(UUID lakeId, Pipeline pipeline);

    Optional<LakeFeatureStatus> findByLakeIdAndFeatureType(UUID lakeId, FeatureType featureType);

    Optional<LakeFeatureStatus> findByLakeIdAndFeatureTypeAndPipeline(
            UUID lakeId,
            FeatureType featureType,
            Pipeline pipeline
    );
}
