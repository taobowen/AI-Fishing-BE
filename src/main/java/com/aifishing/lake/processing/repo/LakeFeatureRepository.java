package com.aifishing.lake.processing.repo;

import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LakeFeatureRepository extends JpaRepository<LakeFeature, UUID> {

    List<LakeFeature> findByLakeId(UUID lakeId);

    List<LakeFeature> findByLakeIdAndPipeline(UUID lakeId, Pipeline pipeline);

    List<LakeFeature> findByLakeIdAndType(UUID lakeId, FeatureType type);

    List<LakeFeature> findByLakeIdAndPipelineAndType(UUID lakeId, Pipeline pipeline, FeatureType type);

    List<LakeFeature> findByLakeIdAndPipelineAndAnalysisVersion(UUID lakeId, Pipeline pipeline, String analysisVersion);

    List<LakeFeature> findByLakeIdAndPipelineAndTypeAndAnalysisVersion(
            UUID lakeId,
            Pipeline pipeline,
            FeatureType type,
            String analysisVersion
    );

    List<LakeFeature> findByLakeIdAndAnalysisVersion(UUID lakeId, String analysisVersion);

    long countByLakeIdAndPipelineAndAnalysisVersion(UUID lakeId, Pipeline pipeline, String analysisVersion);

    @Query("""
            select f from LakeFeature f
            where f.lakeId = :lakeId
              and f.pipeline = :pipeline
              and f.analysisVersion = :analysisVersion
              and f.type in :types
              and (
                    (f.minDepthM is null and f.maxDepthM is null)
                    or (
                        abs(coalesce(f.minDepthM, f.maxDepthM)) >= :depthMin
                        and abs(coalesce(f.minDepthM, f.maxDepthM)) <= :depthMax
                        and abs(coalesce(f.maxDepthM, f.minDepthM)) >= :depthMin
                        and abs(coalesce(f.maxDepthM, f.minDepthM)) <= :depthMax
                    )
              )
            """)
    List<LakeFeature> findCandidates(
            @Param("lakeId") UUID lakeId,
            @Param("pipeline") Pipeline pipeline,
            @Param("analysisVersion") String analysisVersion,
            @Param("types") Collection<FeatureType> types,
            @Param("depthMin") BigDecimal depthMin,
            @Param("depthMax") BigDecimal depthMax
    );

    void deleteByLakeIdAndPipelineAndType(UUID lakeId, Pipeline pipeline, FeatureType type);

    @Modifying
    @Query(value = """
            delete from lake_features f
             where f.lake_id = :lakeId
               and f.pipeline = :pipeline
               and f.type = :type
               and not exists (
                    select 1 from trip_waypoints w
                     where w.lake_feature_id = f.id
               )
            """, nativeQuery = true)
    int deleteUnreferencedByLakeIdAndPipelineAndType(
            @Param("lakeId") UUID lakeId,
            @Param("pipeline") String pipeline,
            @Param("type") String type
    );

    long countByLakeIdAndType(UUID lakeId, FeatureType type);

    long countByLakeIdAndPipelineAndType(UUID lakeId, Pipeline pipeline, FeatureType type);
}
