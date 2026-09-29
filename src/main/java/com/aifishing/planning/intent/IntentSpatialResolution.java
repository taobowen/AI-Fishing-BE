package com.aifishing.planning.intent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intent_spatial_resolutions")
public class IntentSpatialResolution {

    @Id
    private UUID id;

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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public IntentOriginKind getOriginKind() {
        return originKind;
    }

    public void setOriginKind(IntentOriginKind originKind) {
        this.originKind = originKind;
    }

    public UUID getOriginTemplateTargetId() {
        return originTemplateTargetId;
    }

    public void setOriginTemplateTargetId(UUID originTemplateTargetId) {
        this.originTemplateTargetId = originTemplateTargetId;
    }

    public UUID getOriginRequiredPointId() {
        return originRequiredPointId;
    }

    public void setOriginRequiredPointId(UUID originRequiredPointId) {
        this.originRequiredPointId = originRequiredPointId;
    }

    public UUID getSpatialSnapshotId() {
        return spatialSnapshotId;
    }

    public void setSpatialSnapshotId(UUID spatialSnapshotId) {
        this.spatialSnapshotId = spatialSnapshotId;
    }

    public String getMatchingVersion() {
        return matchingVersion;
    }

    public void setMatchingVersion(String matchingVersion) {
        this.matchingVersion = matchingVersion;
    }

    public double getTemplatePointRadiusM() {
        return templatePointRadiusM;
    }

    public void setTemplatePointRadiusM(double templatePointRadiusM) {
        this.templatePointRadiusM = templatePointRadiusM;
    }

    public double getTemplatePathCorridorM() {
        return templatePathCorridorM;
    }

    public void setTemplatePathCorridorM(double templatePathCorridorM) {
        this.templatePathCorridorM = templatePathCorridorM;
    }

    public double getRequiredPointRadiusM() {
        return requiredPointRadiusM;
    }

    public void setRequiredPointRadiusM(double requiredPointRadiusM) {
        this.requiredPointRadiusM = requiredPointRadiusM;
    }

    public int getMaxMatches() {
        return maxMatches;
    }

    public void setMaxMatches(int maxMatches) {
        this.maxMatches = maxMatches;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public UUID getFeatureId() {
        return featureId;
    }

    public void setFeatureId(UUID featureId) {
        this.featureId = featureId;
    }

    public Double getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(Double distanceM) {
        this.distanceM = distanceM;
    }

    public Double getOverlapM() {
        return overlapM;
    }

    public void setOverlapM(Double overlapM) {
        this.overlapM = overlapM;
    }

    public boolean isSyntheticFallback() {
        return syntheticFallback;
    }

    public void setSyntheticFallback(boolean syntheticFallback) {
        this.syntheticFallback = syntheticFallback;
    }

    public IntentMatch toMatch() {
        return new IntentMatch(fishingTargetId, featureId, rank, distanceM, overlapM, syntheticFallback);
    }
}
