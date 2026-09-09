package com.aifishing.feedback.effort.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fishing_effort_segments")
public class FishingEffortSegment {

    @Id
    private UUID id;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "trip_waypoint_id")
    private UUID tripWaypointId;

    @Column(name = "lake_feature_id")
    private UUID lakeFeatureId;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false)
    private EffortSegmentType segmentType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "representative_location", columnDefinition = "geometry(Point,4326)")
    private Point representativeLocation;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "track_geometry", columnDefinition = "geometry(LineString,4326)")
    private LineString trackGeometry;

    @Column(nullable = false)
    private BigDecimal confidence;

    @Column(name = "derivation_version", nullable = false)
    private String derivationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public UUID getTripWaypointId() {
        return tripWaypointId;
    }

    public void setTripWaypointId(UUID tripWaypointId) {
        this.tripWaypointId = tripWaypointId;
    }

    public UUID getLakeFeatureId() {
        return lakeFeatureId;
    }

    public void setLakeFeatureId(UUID lakeFeatureId) {
        this.lakeFeatureId = lakeFeatureId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public EffortSegmentType getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(EffortSegmentType segmentType) {
        this.segmentType = segmentType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Point getRepresentativeLocation() {
        return representativeLocation;
    }

    public void setRepresentativeLocation(Point representativeLocation) {
        this.representativeLocation = representativeLocation;
    }

    public LineString getTrackGeometry() {
        return trackGeometry;
    }

    public void setTrackGeometry(LineString trackGeometry) {
        this.trackGeometry = trackGeometry;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getDerivationVersion() {
        return derivationVersion;
    }

    public void setDerivationVersion(String derivationVersion) {
        this.derivationVersion = derivationVersion;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
