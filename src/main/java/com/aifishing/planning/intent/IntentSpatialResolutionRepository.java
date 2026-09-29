package com.aifishing.planning.intent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IntentSpatialResolutionRepository extends JpaRepository<IntentSpatialResolution, UUID> {

    @Query("""
            SELECT r FROM IntentSpatialResolution r
            WHERE r.originTemplateTargetId = :originId
              AND r.spatialSnapshotId = :snapshotId
              AND r.matchingVersion = :version
              AND r.templatePointRadiusM = :pointRadius
              AND r.templatePathCorridorM = :pathCorridor
              AND r.requiredPointRadiusM = :requiredRadius
              AND r.maxMatches = :maxMatches
            ORDER BY r.rank ASC
            """)
    List<IntentSpatialResolution> findTemplate(
            @Param("originId") UUID originId,
            @Param("snapshotId") UUID snapshotId,
            @Param("version") String version,
            @Param("pointRadius") double pointRadius,
            @Param("pathCorridor") double pathCorridor,
            @Param("requiredRadius") double requiredRadius,
            @Param("maxMatches") int maxMatches
    );

    @Query("""
            SELECT r FROM IntentSpatialResolution r
            WHERE r.originRequiredPointId = :originId
              AND r.spatialSnapshotId = :snapshotId
              AND r.matchingVersion = :version
              AND r.templatePointRadiusM = :pointRadius
              AND r.templatePathCorridorM = :pathCorridor
              AND r.requiredPointRadiusM = :requiredRadius
              AND r.maxMatches = :maxMatches
            ORDER BY r.rank ASC
            """)
    List<IntentSpatialResolution> findRequired(
            @Param("originId") UUID originId,
            @Param("snapshotId") UUID snapshotId,
            @Param("version") String version,
            @Param("pointRadius") double pointRadius,
            @Param("pathCorridor") double pathCorridor,
            @Param("requiredRadius") double requiredRadius,
            @Param("maxMatches") int maxMatches
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM IntentSpatialResolution r
            WHERE r.originTemplateTargetId = :originId
              AND r.spatialSnapshotId = :snapshotId
              AND r.matchingVersion = :version
              AND r.templatePointRadiusM = :pointRadius
              AND r.templatePathCorridorM = :pathCorridor
              AND r.requiredPointRadiusM = :requiredRadius
              AND r.maxMatches = :maxMatches
            """)
    void deleteTemplate(
            @Param("originId") UUID originId,
            @Param("snapshotId") UUID snapshotId,
            @Param("version") String version,
            @Param("pointRadius") double pointRadius,
            @Param("pathCorridor") double pathCorridor,
            @Param("requiredRadius") double requiredRadius,
            @Param("maxMatches") int maxMatches
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM IntentSpatialResolution r
            WHERE r.originRequiredPointId = :originId
              AND r.spatialSnapshotId = :snapshotId
              AND r.matchingVersion = :version
              AND r.templatePointRadiusM = :pointRadius
              AND r.templatePathCorridorM = :pathCorridor
              AND r.requiredPointRadiusM = :requiredRadius
              AND r.maxMatches = :maxMatches
            """)
    void deleteRequired(
            @Param("originId") UUID originId,
            @Param("snapshotId") UUID snapshotId,
            @Param("version") String version,
            @Param("pointRadius") double pointRadius,
            @Param("pathCorridor") double pathCorridor,
            @Param("requiredRadius") double requiredRadius,
            @Param("maxMatches") int maxMatches
    );
}
