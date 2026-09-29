package com.aifishing.planning.intent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_planning_intent_resolutions")
public class TripPlanningIntentResolution {

    @Id
    private UUID id;

    @Column(name = "planning_input_target_id", nullable = false)
    private UUID planningInputTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_kind", nullable = false, length = 32)
    private IntentOriginKind originKind;

    @Column(name = "origin_template_target_id")
    private UUID originTemplateTargetId;

    @Column(name = "origin_required_point_id")
    private UUID originRequiredPointId;

    @Column(name = "spatial_snapshot_id", nullable = false)
    private UUID spatialSnapshotId;

    @Column(name = "matching_version", nullable = false, length = 64)
    private String matchingVersion;

    @Column(name = "template_point_radius_m", nullable = false)
    private double templatePointRadiusM;

    @Column(name = "template_path_corridor_m", nullable = false)
    private double templatePathCorridorM;

    @Column(name = "required_point_radius_m", nullable = false)
    private double requiredPointRadiusM;

    @Column(name = "max_matches", nullable = false)
    private int maxMatches;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "overlap_m")
    private Double overlapM;

    @Column(name = "synthetic_fallback", nullable = false)
    private boolean syntheticFallback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setPlanningInputTargetId(UUID planningInputTargetId) {
        this.planningInputTargetId = planningInputTargetId;
    }

    public void setOriginKind(IntentOriginKind originKind) {
        this.originKind = originKind;
    }

    public void setOriginTemplateTargetId(UUID originTemplateTargetId) {
        this.originTemplateTargetId = originTemplateTargetId;
    }

    public void setOriginRequiredPointId(UUID originRequiredPointId) {
        this.originRequiredPointId = originRequiredPointId;
    }

    public void setSpatialSnapshotId(UUID spatialSnapshotId) {
        this.spatialSnapshotId = spatialSnapshotId;
    }

    public void setMatchingVersion(String matchingVersion) {
        this.matchingVersion = matchingVersion;
    }

    public void setTemplatePointRadiusM(double templatePointRadiusM) {
        this.templatePointRadiusM = templatePointRadiusM;
    }

    public void setTemplatePathCorridorM(double templatePathCorridorM) {
        this.templatePathCorridorM = templatePathCorridorM;
    }

    public void setRequiredPointRadiusM(double requiredPointRadiusM) {
        this.requiredPointRadiusM = requiredPointRadiusM;
    }

    public void setMaxMatches(int maxMatches) {
        this.maxMatches = maxMatches;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public void setFeatureId(UUID featureId) {
        this.featureId = featureId;
    }

    public void setDistanceM(Double distanceM) {
        this.distanceM = distanceM;
    }

    public void setOverlapM(Double overlapM) {
        this.overlapM = overlapM;
    }

    public void setSyntheticFallback(boolean syntheticFallback) {
        this.syntheticFallback = syntheticFallback;
    }

    public UUID getPlanningInputTargetId() {
        return planningInputTargetId;
    }

    public UUID getOriginTemplateTargetId() {
        return originTemplateTargetId;
    }

    public UUID getOriginRequiredPointId() {
        return originRequiredPointId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public boolean isSyntheticFallback() {
        return syntheticFallback;
    }
}
